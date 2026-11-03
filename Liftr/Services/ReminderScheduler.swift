import Foundation
import SwiftData
import UserNotifications

/// Local training reminders, planned on the device (no server or account):
/// fixed training days, a nudge after X days without training, and a weekly summary.
/// Re-planned whenever Liftr opens, a workout is finished, or iCloud brings in a workout from
/// the Watch or iPad — so a reminder for a day she already trained is dropped.
enum ReminderScheduler {
    enum Key {
        static let daysOn = "reminder.days.on"
        static let days = "reminder.days"          // "2,4,6" = Mon, Wed, Fri (Calendar weekday numbers)
        static let time = "reminder.time"          // minutes after midnight
        static let inactiveOn = "reminder.inactive.on"
        static let inactiveDays = "reminder.inactive.days"
        static let weeklyOn = "reminder.weekly.on"
    }

    private static let prefix = "liftr.reminder."
    private static var defaults: UserDefaults { .standard }

    static var time: Int {
        defaults.object(forKey: Key.time) as? Int ?? (18 * 60 + 30)
    }

    static var weekdays: Set<Int> {
        let raw = defaults.string(forKey: Key.days) ?? "2,4,6"
        return Set(raw.split(separator: ",").compactMap { Int($0) })
    }

    static var inactiveDays: Int {
        let value = defaults.integer(forKey: Key.inactiveDays)
        return value > 0 ? value : 3
    }

    static var anyOn: Bool {
        defaults.bool(forKey: Key.daysOn) || defaults.bool(forKey: Key.inactiveOn) || defaults.bool(forKey: Key.weeklyOn)
    }

    static func requestPermission() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    @MainActor
    static func reschedule(_ context: ModelContext) {
        let descriptor = FetchDescriptor<Workout>(sortBy: [SortDescriptor(\.startDate, order: .reverse)])
        let workouts = (try? context.fetch(descriptor)) ?? []
        reschedule(workouts: workouts)
    }

    @MainActor
    static func reschedule(workouts: [Workout]) {
        let done = WorkoutStats.finished(workouts)
        let lastWorkout = done.map(\.startDate).max()
        let streak = WorkoutStats.weekStreak(workouts)
        let weekCount = WorkoutStats.workoutsThisWeek(workouts).count
        let weekVolume = WorkoutStats.workoutsThisWeek(workouts).reduce(0) { $0 + WorkoutStats.volume($1) }
        let trainedDays = Set(done.map { Calendar.current.startOfDay(for: $0.startDate) })
        let hasActive = workouts.contains { $0.endDate == nil }

        var requests: [UNNotificationRequest] = []
        let cal = Calendar.current
        let now = Date()
        let minutes = time

        func at(_ day: Date) -> Date? {
            cal.date(bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: day)
        }

        // 1. Fixed training days: the next 14 days, skipping days already trained.
        if defaults.bool(forKey: Key.daysOn) {
            let days = weekdays
            for offset in 0..<14 {
                guard let day = cal.date(byAdding: .day, value: offset, to: cal.startOfDay(for: now)),
                      days.contains(cal.component(.weekday, from: day)),
                      !trainedDays.contains(day),
                      let fire = at(day), fire > now else { continue }
                if offset == 0, hasActive { continue }
                let content = UNMutableNotificationContent()
                content.title = tr("Time to train 💪", "Tijd voor je training 💪")
                content.body = streak > 0
                    ? tr("Keep your \(streak)-week streak going.", "Houd je reeks van \(streak) weken vast.")
                    : tr("Your workout is planned for today.", "Je training staat vandaag gepland.")
                content.sound = .default
                requests.append(request("day.\(offset)", content, fire))
            }
        }

        // 2. Nudge after X days without training.
        if defaults.bool(forKey: Key.inactiveOn), let last = lastWorkout {
            let n = inactiveDays
            if let day = cal.date(byAdding: .day, value: n, to: cal.startOfDay(for: last)), let fire = at(day), fire > now {
                let content = UNMutableNotificationContent()
                content.title = tr("\(n) days without training", "Al \(n) dagen niet getraind")
                content.body = streak > 0
                    ? tr("Your \(streak)-week streak is at risk 🔥", "Je reeks van \(streak) weken loopt gevaar 🔥")
                    : tr("A short workout today? Every rep counts.", "Vandaag een korte training? Elke herhaling telt.")
                content.sound = .default
                requests.append(request("inactive", content, fire))
            }
        }

        // 3. Weekly summary on Sunday at 20:00.
        if defaults.bool(forKey: Key.weeklyOn) {
            var components = DateComponents()
            components.weekday = 1 // Sunday
            components.hour = 20
            components.minute = 0
            if let fire = cal.nextDate(after: now, matching: components, matchingPolicy: .nextTime) {
                let isThisWeek = WorkoutStats.calendar.isDate(fire, equalTo: now, toGranularity: .weekOfYear)
                let content = UNMutableNotificationContent()
                content.title = tr("Your week in Liftr", "Jouw week in Liftr")
                if isThisWeek, weekCount > 0 {
                    let volume = WeightUnit.current.volume(weekVolume)
                    content.body = tr("This week: \(weekCount) workout\(weekCount == 1 ? "" : "s"), \(volume) lifted. Great job!",
                                      "Deze week \(weekCount) training\(weekCount == 1 ? "" : "en"), \(volume) getild. Goed bezig!")
                } else {
                    content.body = tr("No workouts this week yet — next week is a fresh start.",
                                      "Deze week nog niet getraind — volgende week is een nieuwe kans.")
                }
                content.sound = .default
                requests.append(request("weekly", content, fire))
            }
        }

        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { pending in
            let old = pending.map(\.identifier).filter { $0.hasPrefix(prefix) }
            center.removePendingNotificationRequests(withIdentifiers: old)
            for r in requests { center.add(r) }
        }
    }

    private static func request(_ id: String, _ content: UNMutableNotificationContent, _ date: Date) -> UNNotificationRequest {
        let parts = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        let trigger = UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)
        return UNNotificationRequest(identifier: prefix + id, content: content, trigger: trigger)
    }
}
