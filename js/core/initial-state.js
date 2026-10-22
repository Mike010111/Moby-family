import { unitStart } from './rules.js';
import { artifactsState } from './artifacts.js';

/**
 * Фабрика исходного состояния демо-сессии.
 * Время операций считается от «сейчас», чтобы лимиты и аналитика
 * выглядели живыми в любой день защиты.
 */

const MIN = 60_000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;

export const STATE_VERSION = 8;

export function createInitialState() {
  const now = Date.now();
  const tx = (id, title, amount, mcc, ago, extra = {}) => ({ id, title, amount, mcc, ts: now - ago, status: 'ok', ...extra });
  // Сегодняшние операции остаются сегодняшними, в какое бы время ни запустили демо
  const todayStart = unitStart(now, 'day');
  const today = (id, title, amount, mcc, ago, extra) => {
    const t = tx(id, title, amount, mcc, ago, extra);
    if (t.ts < todayStart) t.ts = todayStart + (now - todayStart) * (1 - ago / DAY); // сжимаем в «сегодня», порядок сохраняется
    return t;
  };
  // Лимиты календарные: период начинается с начала дня / недели / месяца
  const limit = (id, count, unit, amount, categories) => ({ id, count, unit, amount, categories, start: unitStart(now, unit) });

  return {
    version: STATE_VERSION,

    ui: {
      role: 'parent', // parent | kid | teen — активная вкладка жюри
      selectedChildId: 'misha', // ребёнок, открытый в хабе
    },

    /** Главный родственник — владелец хаба Moby-Family. */
    parent: {
      id: 'mom',
      name: 'Анна',
      balance: 842.6,
      points: 3420, // баллы родителя — отдельный счёт
      cardMask: '•• 7710',
      packageName: 'Семейный пакет Moby',
    },

    /**
     * Родственники, добавленные в семью. label — как родственник записан у ребёнка,
     * genitive/dative/accusative — падежи для подписей («Попросить у мамы», «Запрос отправлен папе»). Окно в демо есть только у главного (main).
     */
    relatives: [
      { id: 'mom', label: 'Мама', genitive: 'мамы', dative: 'маме', accusative: 'маму', name: 'Анна', main: true, female: true },
      { id: 'dad', label: 'Папа', genitive: 'папы', dative: 'папе', accusative: 'папу', name: 'Сергей' },
      { id: 'grandma', label: 'Бабушка', genitive: 'бабушки', dative: 'бабушке', accusative: 'бабушку', name: 'Галина', female: true },
    ],

    children: {
      misha: {
        id: 'misha',
        name: 'Миша',
        nameGen: 'Миши',
        surname: 'Ромейко',
        age: 9,
        cohort: 'junior',
        balance: 12.4,
        points: 320, // баллы ребёнка — изолированы от родительских; ими платят в магазине артефактов
        card: {
          title: 'Карта учащегося',
          mask: '•• 7890',
          number: '5321 4812 3456 7890',
          validThru: '09/29',
          studentId: '2024101234',
          holder: 'ROMEYKO MIKE',
          image: 'assets/cards/student_card_kid.webp?v=2',
          frozen: false,
        },
        artifacts: artifactsState(now, 'junior', {
          glasses: { id: 'glasses-blue', boughtWeeksAgo: 2 },
        }),
        allow: {
          food: true,
          cafe: true,
          fastfood: false,
          transport: true,
          clothes: false,
          electronics: false,
          books: true,
          education: true,
          sport: true,
          pharmacy: true,
          games: false,
          entertainment: true,
          subscriptions: false,
          online: false,
          cash: false,
        },
        limits: [
          limit('l-m1', 1, 'day', 15, []),
          limit('l-m2', 1, 'week', 20, ['cafe']),
        ],
        autopay: { enabled: true, amount: 10, every: 'week', weekday: 0, monthDay: 1 },
        piggies: [{ id: 'pg-bike', goal: 'Велосипед', target: 250, saved: 86.5 }],
        tasks: [
          { id: 't1', title: 'Прочитать 20 страниц книги', reward: 2, status: 'open' },
          { id: 't2', title: 'Получить 10 по математике', reward: 3, status: 'open' },
          { id: 't3', title: 'Убрать в комнате', reward: 1.5, status: 'done' },
        ],
        lessons: { done: 3 },
        geo: { place: 'Школа №12', x: 0.68, y: 0.32, distance: 1.2, updated: now - 2 * MIN },
        battery: 64,
        approvals: [],
        inbox: [],
        tx: [
          today('x1', 'Буфет школы №12', -2.4, '5462', 3 * HOUR),
          today('x2', 'Канцтовары «Скрепка»', -1.2, '5943', 5 * HOUR),
          tx('x3', 'Автоперевод от мамы', 10, 'topup', 2 * DAY),
          tx('x4', 'Буфет школы №12', -2.1, '5462', 2 * DAY + 4 * HOUR),
          tx('x5', 'Книжный «Академкнига»', -6.8, '5942', 9 * DAY),
        ],
      },

      ivan: {
        id: 'ivan',
        name: 'Иван',
        nameGen: 'Ивана',
        surname: 'Иванов',
        age: 17,
        cohort: 'teen',
        balance: 64.2,
        points: 860,
        card: {
          title: 'Карта учащегося',
          mask: '•• 7891',
          number: '5321 4812 3456 7891',
          validThru: '10/30',
          studentId: '2024101235',
          holder: 'IVANOV IVAN',
          image: 'assets/cards/student_card_teen.webp?v=2',
          frozen: false,
        },
        artifacts: artifactsState(now, 'teen', {
          glasses: { id: 'glasses-dark', boughtWeeksAgo: 2 },
          outer: { id: 'vest-brown', boughtWeeksAgo: 1 },
        }),
        allow: {
          food: true,
          cafe: true,
          fastfood: true,
          transport: true,
          clothes: true,
          electronics: true,
          books: true,
          education: true,
          sport: true,
          pharmacy: true,
          games: false,
          entertainment: true,
          subscriptions: true,
          online: true,
          cash: true,
          p2p: true,
        },
        limits: [
          limit('l-a1', 1, 'day', 60, []),
          limit('l-a2', 1, 'week', 30, ['p2p']),
        ],
        autopay: { enabled: true, amount: 40, every: 'week', weekday: 4, monthDay: 1 },
        piggies: [
          { id: 'pg-laptop', goal: 'Ноутбук', target: 1200, saved: 310 },
          { id: 'pg-concert', goal: 'Билет на концерт', target: 60, saved: 52 },
        ],
        tasks: [
          { id: 'ta1', title: 'Сдать пробный ЦТ по математике', reward: 10, status: 'open' },
          { id: 'ta2', title: 'Сходить в магазин за продуктами', reward: 3, status: 'done' },
        ],
        lessons: { done: 7 },
        subscriptions: [
          { id: 'sub1', title: 'Музыкальный стриминг', price: 6.99, next: '12 ноя', active: true, icon: 'music' },
          { id: 'sub2', title: 'Курс английского', price: 15, next: '3 ноя', active: true, icon: 'grad' },
          { id: 'sub3', title: 'Облако 100 ГБ', price: 3.49, next: '20 ноя', active: false, icon: 'cloud' },
        ],
        geo: { place: 'Библиотека', x: 0.34, y: 0.72, distance: 1.6, updated: now - 6 * MIN },
        battery: 38,
        approvals: [],
        inbox: [],
        stats: { txCount: 1243, since: '2022' },
        tx: [
          today('y1', 'Кофейня «Зерно»', -6.5, '5812', 2 * HOUR),
          today('y2', 'Перевод: Катя Соколова', -5.5, '4829', 4 * HOUR),
          tx('y3', 'Музыкальный стриминг', -6.99, '5735', 3 * DAY, { online: true }),
          tx('y4', 'Автоперевод от мамы', 40, 'topup', 4 * DAY),
          tx('y5', 'Книжный «Академкнига»', -18.4, '5942', 5 * DAY),
          tx('y6', 'Такси', -9.2, '4121', 12 * DAY),
          tx('y7', 'Одежда «Mark Formelle»', -42, '5651', 18 * DAY),
          tx('y8', 'Курс английского', -15, '8299', 26 * DAY, { online: true }),
        ],
      },
    },

    /** Запросы детей к родственникам: пополнение и перевод сверх лимита. */
    requests: [],

    /** Лента событий главного родственника. */
    feed: [
      {
        id: 'f0',
        kind: 'info',
        childId: 'ivan',
        title: 'Иван оплатил «Кофейня «Зерно»»',
        text: '6,50 BYN · Кафе и столовые',
        ts: now - 2 * HOUR,
      },
    ],

    /** Журнал действий для панели жюри. */
    log: [],
  };
}
