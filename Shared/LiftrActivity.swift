#if os(iOS)
import ActivityKit
import Foundation

/// Live Activity shown on the Lock Screen and in the Dynamic Island during a workout.
struct LiftrActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var exerciseName: String
        var setsDone: Int
        var totalSets: Int
        /// When the rest timer ends; nil when not resting.
        var restEnd: Date?
    }

    var workoutTitle: String
    var startDate: Date
}
#endif
