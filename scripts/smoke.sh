#!/usr/bin/env bash
# Сквозная проверка ключевых сценариев через gateway (стенд из `docker compose up -d`).
# Требует curl и python3. Адреса переопределяются: GATEWAY=… KEYCLOAK=… scripts/smoke.sh
set -euo pipefail

GATEWAY=${GATEWAY:-http://localhost:8080}
KEYCLOAK=${KEYCLOAK:-http://localhost:8180}
STATUS_FILE=$(mktemp)
BODY_FILE=$(mktemp)
trap 'rm -f "$STATUS_FILE" "$BODY_FILE"' EXIT

pass() { printf '  \033[32m✔\033[0m %s\n' "$1"; }
fail() { printf '  \033[31m✘ %s\033[0m\n' "$1"; printf '    ответ: %s\n' "$(cat "$BODY_FILE")"; exit 1; }
step() { printf '\n\033[1m%s\033[0m\n' "$1"; }

# json <путь> — значение поля из последнего ответа (путь через точки, индексы — числа)
json() {
  python3 - "$1" "$BODY_FILE" <<'PY'
import json, sys
path, file = sys.argv[1], sys.argv[2]
v = json.load(open(file))
for key in filter(None, path.split('.')):
    v = v[int(key)] if isinstance(v, list) else v.get(key)
    if v is None:
        break
print('' if v is None else (json.dumps(v, ensure_ascii=False) if isinstance(v, (dict, list)) else v))
PY
}

token() {
  curl -sf -X POST "$KEYCLOAK/realms/moby/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=moby-frontend -d username="$1" -d password=demo \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])'
}

# call <токен> <метод> <путь> [тело] — пишет тело в $BODY_FILE, код ответа — в $STATUS_FILE
call() {
  local args=(-s -o "$BODY_FILE" -w '%{http_code}' -X "$2" "$GATEWAY$3" -H "Authorization: Bearer $1" -H "Idempotency-Key: smoke-$RANDOM-$RANDOM-$(date +%s%N)")
  if [ $# -ge 4 ]; then args+=(-H 'Content-Type: application/json' -d "$4"); fi
  curl "${args[@]}" > "$STATUS_FILE"
}
status() { cat "$STATUS_FILE"; }
expect_status() { [ "$(status)" = "$1" ] || fail "$2: ожидали HTTP $1, получили $(status)"; }
expect_eq() { [ "$1" = "$2" ] || fail "$3: ожидали «$2», получили «$1»"; }
money_eq() { python3 -c "import sys; sys.exit(0 if abs(float('$1') - float('$2')) < 0.001 else 1)" || fail "$3: ожидали $2, получили $1"; }
add() { python3 -c "print(round($1 + $2, 2))"; }

balance() { call "$1" GET "/api/payment/children/$2/account"; json balance; }
parent_balance() { call "$ANNA" GET /api/payment/me/parent-account; json balance; }

step "0. Вход демо-пользователей через Keycloak и сброс демо-данных"
ANNA=$(token anna) && MISHA=$(token misha) && IVAN=$(token ivan) && OLGA=$(token olga) || fail "Keycloak не выдал токены"
pass "токены anna, misha, ivan, olga"
for svc in payment family rewards notifications; do
  call "$ANNA" POST "/api/$svc/demo/reset"; expect_status 204 "сброс $svc"
done
pass "seed-данные восстановлены"
call "$ANNA" GET /api/payment/children/misha/account; expect_status 200 "родитель видит счёт Миши"
call "$OLGA" GET /api/payment/children/misha/account; expect_status 403 "родитель другой семьи"
call "$IVAN" GET /api/payment/children/misha/account; expect_status 403 "брат"
curl -s -o "$BODY_FILE" -w '%{http_code}' "$GATEWAY/api/family/me" > "$STATUS_FILE"; expect_status 401 "без токена"
pass "права: 200 своей семье, 403 чужой семье и брату, 401 без токена"

step "1. Запрос «5 BYN на обед» от Миши → одобрение → балансы изменились"
K0=$(balance "$MISHA" misha); P0=$(parent_balance)
call "$MISHA" POST /api/family/requests '{"childId":"misha","relativeId":"mom","amount":"5","reason":"На обед"}'
expect_status 201 "запрос создан"; REQ=$(json request.id); expect_eq "$(json request.status)" PENDING "статус запроса"
pass "запрос $REQ ждёт решения"
for _ in $(seq 1 30); do
  call "$ANNA" GET /api/notifications/feed
  [ "$(json 0.requestId)" = "$REQ" ] && break; sleep 0.5
done
expect_eq "$(json 0.title)" "Миша просит 5,00 BYN" "лента родителя (Kafka → notification)"
pass "в ленте Анны: «$(json 0.title)»"
call "$ANNA" POST "/api/family/requests/$REQ/approve"; expect_status 200 "одобрение"
money_eq "$(balance "$MISHA" misha)" "$(add "$K0" 5)" "баланс Миши"
money_eq "$(parent_balance)" "$(add "$P0" -5)" "баланс Анны"
pass "Миша: $K0 → $(balance "$MISHA" misha) BYN, Анна: $P0 → $(parent_balance) BYN"
call "$ANNA" POST "/api/family/requests/$REQ/approve"; expect_status 409 "повторное одобрение"
for _ in $(seq 1 30); do
  call "$MISHA" GET "/api/notifications/inbox?childId=misha"
  [ -n "$(json 0.text)" ] && break; sleep 0.5
done
expect_eq "$(json 0.text)" "Мама одобрила запрос: +5,00 BYN" "уведомление Мише"
pass "уведомление Мише: «$(json 0.text)»; повторное одобрение — 409"

step "2. Оплата в зале игровых автоматов → HARD_BLOCK"
call "$MISHA" POST /api/payment/children/misha/qr-payments '{"merchantId":"m-slots"}'
expect_status 422 "отказ"; expect_eq "$(json code)" HARD_BLOCK "код отказа"
pass "422 $(json code): $(json detail)"

step "3. P2P Ивана сверх лимита → запрос → одобрение → перевод"
call "$IVAN" GET "/api/payment/children/ivan/p2p/quote?amount=35"; expect_eq "$(json mode)" REQUEST "котировка 35 BYN"
call "$IVAN" POST /api/payment/children/ivan/p2p-transfers '{"contactId":"c-katya","amount":"35"}'
expect_status 422 "перевод без одобрения"; expect_eq "$(json code)" NEEDS_APPROVAL "код"
pass "перевод 35 BYN без одобрения: $(json code) — $(json detail)"
call "$IVAN" POST /api/family/requests/p2p-over '{"childId":"ivan","contactId":"c-katya","amount":"35"}'
expect_status 201 "запрос сверх лимита"; P2P_REQ=$(json request.id)
call "$ANNA" POST "/api/family/requests/$P2P_REQ/approve"; expect_status 200 "одобрение перевода"
I0=$(balance "$IVAN" ivan)
call "$IVAN" POST /api/payment/children/ivan/p2p-transfers '{"contactId":"c-katya","amount":"35"}'
expect_status 201 "перевод"; expect_eq "$(json mode)" APPROVED "режим перевода"
money_eq "$(balance "$IVAN" ivan)" "$(add "$I0" -35)" "баланс Ивана"
call "$IVAN" GET "/api/payment/children/ivan/p2p/quote?amount=20"; expect_eq "$(json mode)" REQUEST "одобрение разовое"
pass "перевод выполнен по одобрению: Иван $I0 → $(balance "$IVAN" ivan) BYN; одобрение израсходовано"

step "4. Покупка артефакта → кэшбэк при оплате по QR"
call "$MISHA" POST /api/rewards/children/misha/artifacts/diamond/purchase
expect_status 200 "покупка артефакта"; expect_eq "$(json artifact.cashback)" 5 "кэшбэк артефакта"
pass "Бриллиант куплен за баллы: осталось $(json points)"
B0=$(balance "$MISHA" misha)
call "$MISHA" POST /api/payment/children/misha/qr-payments '{"merchantId":"m-buffet"}'
expect_status 201 "оплата в буфете"
CB_PCT=$(json cashback.pct); CB_VALUE=$(json cashback.value)
money_eq "$CB_VALUE" 0.12 "кэшбэк 5% от 2,40"
B1=$(balance "$MISHA" misha)
money_eq "$B1" "$(add "$B0" -2.28)" "баланс после оплаты и кэшбэка"
pass "оплата 2,40 BYN, кэшбэк $CB_PCT% = $CB_VALUE BYN; баланс $B0 → $B1"

printf '\n\033[32mВсе сценарии пройдены.\033[0m\n'
