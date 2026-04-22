package com.essentials.qx;

import java.util.HashMap;
import java.util.Map;

/**
 * Localization manager for EssentialsQX mod messages
 */
public class LocalizationManager {
    private static final Map<String, Map<String, String>> messages = new HashMap<>();

    static {
        // English messages
        Map<String, String> en = new HashMap<>();
        en.put("command.only_players", "This command can only be used by players!");
        en.put("spawn.not_set", "Spawn point is not set! Ask an operator to use /setspawn first.");
        en.put("teleport.already_active", "You already have an active teleport countdown!");
        en.put("teleport.dimension_unavailable", "Spawn dimension is not available!");
        en.put("teleport.no_safe_location", "Could not find a safe spawn location! Contact an administrator.");
        en.put("teleport.destination_unsafe", "Teleport destination is no longer safe!");
        en.put("teleport.failed", "Teleportation failed!");
        en.put("teleport.start_failed", "Failed to start teleport countdown!");
        en.put("teleport.cancelled_movement", "Teleportation cancelled due to movement!");
        en.put("teleport.on_cooldown", "You must wait %d seconds before teleporting again!");
        en.put("teleport.starting", "Teleportation starting in %d seconds...");
        en.put("teleport.countdown_info", "Countdown will be displayed starting from %d seconds.");
        en.put("teleport.countdown", "Teleporting in %d seconds...");
        en.put("teleport.success", "Teleported!");
        en.put("spawn.set_success", "Spawn point set to your current location!");
        en.put("spawn.set_failed", "Failed to set spawn point!");
        en.put("spawn.reset_success", "Spawn point reset to default location (0, 64, 0)!");
        en.put("spawn.reset_failed", "Failed to reset spawn point!");
        en.put("spawn.location_invalid", "Cannot set spawn here! The location must be safe with solid ground beneath your feet and no water/lava.");
        en.put("spawn.too_close_border", "Cannot set spawn here! Must be at least 3 blocks away from the world border.");
        messages.put("en", en);

        // Russian messages
        Map<String, String> ru = new HashMap<>();
        ru.put("command.only_players", "Эту команду могут использовать только игроки!");
        ru.put("spawn.not_set", "Точка спавна не установлена! Попросите оператора использовать /setspawn.");
        ru.put("teleport.already_active", "У вас уже активен отсчет телепортации!");
        ru.put("teleport.dimension_unavailable", "Измерение спавна недоступно!");
        ru.put("teleport.no_safe_location", "Не удалось найти безопасное место спавна! Свяжитесь с администратором.");
        ru.put("teleport.destination_unsafe", "Место назначения телепортации больше не безопасно!");
        ru.put("teleport.failed", "Телепортация не удалась!");
        ru.put("teleport.start_failed", "Не удалось запустить отсчет телепортации!");
        ru.put("teleport.cancelled_movement", "Телепортация отменена из-за движения!");
        ru.put("teleport.on_cooldown", "Вы должны подождать %d секунд перед следующей телепортацией!");
        ru.put("teleport.starting", "Телепортация начнется через %d секунд...");
        ru.put("teleport.countdown_info", "Отсчет будет показан начиная с %d секунд.");
        ru.put("teleport.countdown", "Телепортация через %d секунд...");
        ru.put("teleport.success", "Телепортировано!");
        ru.put("spawn.set_success", "Точка спавна установлена на ваше текущее местоположение!");
        ru.put("spawn.set_failed", "Не удалось установить точку спавна!");
        ru.put("spawn.reset_success", "Точка спавна сброшена на местоположение по умолчанию (0, 64, 0)!");
        ru.put("spawn.reset_failed", "Не удалось сбросить точку спавна!");
        ru.put("spawn.location_invalid", "Нельзя установить спавн здесь! Местоположение должно быть безопасным с твердой землей под ногами и без воды/лавы.");
        ru.put("spawn.too_close_border", "Нельзя установить спавн здесь! Должно быть не менее 3 блоков от границы мира.");

        // Home system messages
        en.put("home.invalid_name", "Invalid home name! Use only letters and numbers without spaces.");
        en.put("home.dimension_not_allowed", "Cannot set home in this dimension!");
        en.put("home.limit_reached", "You have reached the maximum number of homes (%d)!");
        en.put("home.already_exists", "Home '%s' already exists!");
        en.put("home.location_unsafe", "Cannot set home here! The location must be safe.");
        en.put("home.set_success", "Home '%s' has been set!");
        en.put("home.not_found", "Home '%s' not found!");
        en.put("home.rename_success", "Home '%s' renamed to '%s'!");
        en.put("home.delete_success", "Home '%s' has been deleted!");
        en.put("home.dimension_unavailable", "Home dimension is not available!");
        en.put("home.destination_unsafe", "Home destination is no longer safe!");
        en.put("home.teleport_success", "Teleported to home '%s'!");
        en.put("home.teleport_failed", "Failed to teleport to home!");
        en.put("home.no_homes", "You have no homes set.");
        en.put("home.list_header", "Your homes (%d):");
        en.put("home.list_entry", "  %s: %d, %d, %d in %s");
        en.put("home.player_not_found", "Player '%s' not found or not online!");
        en.put("home.admin_no_homes", "Player '%s' has no homes set.");
        en.put("home.admin_list_header", "Homes of player '%s' (%d):");
        en.put("home.admin_list_entry", "  %s: %d, %d, %d in %s");
        en.put("home.admin_home_not_found", "Home '%s' not found for player '%s'!");
        en.put("home.admin_delete_success", "Deleted home '%s' for player '%s'!");
        en.put("home.admin_home_deleted", "Your home '%s' was deleted by administrator '%s'!");
        en.put("home.admin_teleport_success", "Teleported to home '%s' of player '%s'!");

        ru.put("home.invalid_name", "Неверное имя дома! Используйте только буквы и цифры без пробелов.");
        ru.put("home.dimension_not_allowed", "Нельзя установить дом в этом измерении!");
        ru.put("home.limit_reached", "Вы достигли максимального количества домов (%d)!");
        ru.put("home.already_exists", "Дом '%s' уже существует!");
        ru.put("home.location_unsafe", "Нельзя установить дом здесь! Местоположение должно быть безопасным.");
        ru.put("home.set_success", "Дом '%s' установлен!");
        ru.put("home.not_found", "Дом '%s' не найден!");
        ru.put("home.rename_success", "Дом '%s' переименован в '%s'!");
        ru.put("home.delete_success", "Дом '%s' удален!");
        ru.put("home.dimension_unavailable", "Измерение дома недоступно!");
        ru.put("home.destination_unsafe", "Место назначения дома больше не безопасно!");
        ru.put("home.teleport_success", "Телепортирован к дому '%s'!");
        ru.put("home.teleport_failed", "Не удалось телепортироваться к дому!");
        ru.put("home.no_homes", "У вас нет установленных домов.");
        ru.put("home.list_header", "Ваши дома (%d):");
        ru.put("home.list_entry", "  %s: %d, %d, %d в %s");
        ru.put("home.player_not_found", "Игрок '%s' не найден или не онлайн!");
        ru.put("home.admin_no_homes", "У игрока '%s' нет установленных домов.");
        ru.put("home.admin_list_header", "Дома игрока '%s' (%d):");
        ru.put("home.admin_list_entry", "  %s: %d, %d, %d в %s");
        ru.put("home.admin_home_not_found", "Дом '%s' не найден у игрока '%s'!");
        ru.put("home.admin_delete_success", "Удален дом '%s' игрока '%s'!");
        ru.put("home.admin_home_deleted", "Ваш дом '%s' был удален администратором '%s'!");
        ru.put("home.admin_teleport_success", "Телепортирован к дому '%s' игрока '%s'!");

// RTP system messages
        en.put("rtp.no_points_available", "No RTP points available! An operator must run /rtp create or /tpr create first.");
        en.put("rtp.no_available_points", "No available RTP points! All points have been used.");
        en.put("rtp.dimension_unavailable", "RTP dimension is not available!");
        en.put("rtp.destination_unsafe", "RTP destination is no longer safe!");
        en.put("rtp.success", "Randomly teleported!");
        en.put("rtp.failed", "RTP failed!");
        en.put("rtp.create_started", "§eStarting RTP points creation... Server may experience lag.");
        en.put("rtp.create_completed", "§aRTP points creation completed! Total points: %d");
        en.put("rtp.create_confirm", "This will search for new RTP points and may cause lag. Continue? (Command will run automatically)");
        en.put("rtp.create_failed", "RTP create failed");
        en.put("rtp.generating", "Generating RTP points... (%d/%d)");
        en.put("rtp.generated", "Successfully generated %d RTP points across %d dimensions");
        en.put("rtp.fallback_search", "§eNo prepared RTP points available. Using fallback search...");
        en.put("rtp.no_points_in_dimension", "§cNo safe RTP points found in your current dimension (%dimension%)!");
        en.put("rtp.reset_success", "All RTP data has been reset!");
        en.put("rtp.reset_failed", "Failed to reset RTP data!");
        en.put("rtp.status", "§aRTP points: %d");

        ru.put("rtp.no_points_available", "Точки RTP недоступны! Оператор должен сначала выполнить /rtp create или /tpr create.");
        ru.put("rtp.no_available_points", "Нет доступных точек RTP! Все точки были использованы.");
        ru.put("rtp.dimension_unavailable", "Измерение RTP недоступно!");
        ru.put("rtp.destination_unsafe", "Место назначения RTP больше не безопасно!");
        ru.put("rtp.success", "Случайная телепортация выполнена!");
        ru.put("rtp.failed", "RTP не удался!");
        ru.put("rtp.create_started", "§eЗапуск создания точек RTP... Сервер может лагать.");
        ru.put("rtp.create_completed", "§aСоздание точек RTP завершено! Всего точек: %d");
        ru.put("rtp.create_confirm", "Это приведет к поиску новых точек RTP и может вызвать лаги. Продолжить? (Команда выполнится автоматически)");
        ru.put("rtp.create_failed", "Создание точек RTP не удалось");
        ru.put("rtp.generating", "Генерация точек RTP... (%d/%d)");
        ru.put("rtp.generated", "Успешно сгенерировано %d точек RTP в %d измерениях");
        ru.put("rtp.fallback_search", "§eЗаготовленных точек RTP нет. Используется стандартный поиск...");
        ru.put("rtp.no_points_in_dimension", "§cНе найдено безопасных точек RTP в вашем текущем измерении (%dimension%)!");
        ru.put("rtp.reset_success", "Все данные RTP сброшены!");
        ru.put("rtp.reset_failed", "Не удалось сбросить данные RTP!");
        ru.put("rtp.status", "§aТочек RTP: %d");

// God and Fly commands messages
        en.put("god.enabled", "God mode enabled!");
        en.put("god.disabled", "God mode disabled!");
        en.put("fly.enabled", "Fly mode enabled!");
        en.put("fly.disabled", "Fly mode disabled!");

        ru.put("god.enabled", "Режим бога включен!");
        ru.put("god.disabled", "Режим бога отключен!");
        ru.put("fly.enabled", "Режим полета включен!");
        ru.put("fly.disabled", "Режим полета отключен!");

// TPA system messages
        en.put("tpa.cannot_teleport_to_self", "You cannot teleport to yourself!");
        en.put("tpa.already_have_request", "You already have an active teleport request!");
        en.put("tpa.target_busy", "That player already has a pending teleport request!");
        en.put("tpa.request_sent", "Teleport request sent to %s!");
        en.put("tpa.request_received", "Teleport request received from %s. Use /tpaccept to accept or wait for it to expire.");
        en.put("tpa.no_request", "You don't have any pending teleport requests!");
        en.put("tpa.requester_offline", "The player who sent the request is no longer online!");
        en.put("tpa.requester_on_cooldown", "The requester is on teleport cooldown for %d more seconds!");
        en.put("tpa.you_on_cooldown", "You are on teleport cooldown for %d more seconds!");
        en.put("tpa.destination_unsafe", "Teleport destination is not safe!");
        en.put("tpa.target_location_unsafe", "Your current location is not safe for teleportation!");
        en.put("tpa.requester_busy", "The requester has an active teleport in progress!");
        en.put("tpa.you_have_active_teleport", "You have an active teleport in progress!");
        en.put("tpa.teleport_success", "Teleported to %s!");
        en.put("tpa.teleport_completed", "%s has teleported to you!");
        en.put("tpa.teleport_failed", "Teleport failed!");
        en.put("tpa.teleport_failed_for_target", "Teleport to you failed for %s!");
        en.put("tpa.destination_now_unsafe", "Teleport destination became unsafe!");
        en.put("tpa.your_location_now_unsafe", "Your location became unsafe for teleportation!");
        en.put("tpa.no_outgoing_request", "You don't have any outgoing teleport requests!");
        en.put("tpa.request_cancelled", "Teleport request cancelled!");
        en.put("tpa.request_cancelled_by_sender", "%s cancelled their teleport request!");
        en.put("tpa.request_expired", "Your teleport request expired!");
        en.put("tpa.request_expired_for_target", "Teleport request from %s expired!");

        ru.put("tpa.cannot_teleport_to_self", "Вы не можете телепортироваться к себе!");
        ru.put("tpa.already_have_request", "У вас уже есть активный запрос на телепортацию!");
        ru.put("tpa.target_busy", "У этого игрока уже есть ожидающий запрос на телепортацию!");
        ru.put("tpa.request_sent", "Запрос на телепортацию отправлен игроку %s!");
        ru.put("tpa.request_received", "Получен запрос на телепортацию от %s. Используйте /tpaccept для принятия или дождитесь истечения времени.");
        ru.put("tpa.no_request", "У вас нет ожидающих запросов на телепортацию!");
        ru.put("tpa.requester_offline", "Игрок, отправивший запрос, больше не в сети!");
        ru.put("tpa.requester_on_cooldown", "Отправитель запроса имеет кулдаун телепортации еще %d секунд!");
        ru.put("tpa.you_on_cooldown", "У вас кулдаун телепортации еще %d секунд!");
        ru.put("tpa.destination_unsafe", "Место назначения телепортации небезопасно!");
        ru.put("tpa.target_location_unsafe", "Ваше текущее местоположение небезопасно для телепортации!");
        ru.put("tpa.requester_busy", "Отправитель запроса имеет активную телепортацию!");
        ru.put("tpa.you_have_active_teleport", "У вас есть активная телепортация!");
        ru.put("tpa.teleport_success", "Телепортирован к %s!");
        ru.put("tpa.teleport_completed", "%s телепортировался к вам!");
        ru.put("tpa.teleport_failed", "Телепортация не удалась!");
        ru.put("tpa.teleport_failed_for_target", "Телепортация к вам не удалась для %s!");
        ru.put("tpa.destination_now_unsafe", "Место назначения стало небезопасным!");
        ru.put("tpa.your_location_now_unsafe", "Ваше местоположение стало небезопасным для телепортации!");
        ru.put("tpa.no_outgoing_request", "У вас нет исходящих запросов на телепортацию!");
        ru.put("tpa.request_cancelled", "Запрос на телепортацию отменен!");
        ru.put("tpa.request_cancelled_by_sender", "%s отменил свой запрос на телепортацию!");
        ru.put("tpa.request_expired", "Ваш запрос на телепортацию истек!");
        ru.put("tpa.request_expired_for_target", "Запрос на телепортацию от %s истек!");

// Services messages
        en.put("services.vein_mining.enabled", "Vein Mining is now enabled!");
        en.put("services.vein_mining.disabled", "Vein Mining is now disabled!");
        en.put("services.falling_trees.enabled", "Falling Trees is now enabled!");
        en.put("services.falling_trees.disabled", "Falling Trees is now disabled!");

        ru.put("services.vein_mining.enabled", "Добыча жил включена!");
        ru.put("services.vein_mining.disabled", "Добыча жил отключена!");
        ru.put("services.falling_trees.enabled", "Валка деревьев включена!");
        ru.put("services.falling_trees.disabled", "Валка деревьев отключена!");

        messages.put("en", en);
        messages.put("ru", ru);
    }

    /**
     * Get localized message
     */
    public static String getMessage(String key) {
        String lang = EssentialsQXMod.getConfig().language;
        Map<String, String> langMessages = messages.get(lang);
        if (langMessages == null) {
            langMessages = messages.get("ru"); // Fallback to Russian
        }
        String message = langMessages.get(key);
        return message != null ? message : "MISSING_TRANSLATION: " + key;
    }

    /**
     * Get localized message with parameters
     */
    public static String getMessage(String key, Object... args) {
        String message = getMessage(key);
        return String.format(message, args);
    }
}