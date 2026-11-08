package by.moby.common.events;

/**
 * Кто совершил действие — для текстов уведомлений: «Мама одобрила запрос».
 *
 * @param memberId id участника семьи
 * @param label    как участник подписан у ребёнка («Мама»)
 * @param female   для согласования глагола («одобрила» / «одобрил»)
 */
public record Actor(String memberId, String label, boolean female) {
}
