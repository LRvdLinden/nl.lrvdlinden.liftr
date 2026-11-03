import Foundation
import SwiftUI

enum WeightUnit: String, CaseIterable, Identifiable {
    case kg, lb

    var id: String { rawValue }

    static let storageKey = "weightUnit"

    static var current: WeightUnit {
        WeightUnit(rawValue: UserDefaults.standard.string(forKey: storageKey) ?? "kg") ?? .kg
    }

    var symbol: String { self == .kg ? "kg" : "lb" }

    /// Smallest plate step used by the +/− buttons.
    var step: Double { self == .kg ? 2.5 : 5 }

    func fromKg(_ kg: Double) -> Double { self == .kg ? kg : kg * 2.204_622_6 }
    func toKg(_ value: Double) -> Double { self == .kg ? value : value / 2.204_622_6 }

    /// "42,5" (number only, in this unit).
    func number(_ kg: Double) -> String {
        LiftrFormat.decimal(fromKg(kg))
    }

    /// "42,5 kg"
    func format(_ kg: Double) -> String {
        "\(number(kg)) \(symbol)"
    }

    /// Volume in tonnes / kilos: "12,4 t" or "860 kg".
    func volume(_ kg: Double) -> String {
        let value = fromKg(kg)
        if self == .kg, value >= 10_000 { return "\(LiftrFormat.decimal(value / 1000)) t" }
        if self == .lb, value >= 10_000 { return "\(LiftrFormat.decimal(value / 1000))k lb" }
        return "\(LiftrFormat.decimal(value.rounded())) \(symbol)"
    }
}

enum LiftrFormat {
    static func decimal(_ value: Double, maxFraction: Int = 1) -> String {
        value.formatted(.number.precision(.fractionLength(0...maxFraction)).locale(.current))
    }

    static func duration(_ seconds: TimeInterval) -> String {
        let total = Int(max(0, seconds))
        let h = total / 3600, m = (total % 3600) / 60, s = total % 60
        if h > 0 { return String(format: "%d:%02d:%02d", h, m, s) }
        return String(format: "%d:%02d", m, s)
    }

    /// "1 u 12 min" / "1 h 12 min"
    static func longDuration(_ seconds: TimeInterval) -> String {
        let total = Int(max(0, seconds)) / 60
        let h = total / 60, m = total % 60
        if h > 0 { return "\(h) \(tr("h", "u")) \(m) min" }
        return "\(m) min"
    }

    static func day(_ date: Date) -> String {
        date.formatted(.dateTime.weekday(.wide).day().month(.wide).locale(L10n.locale))
    }

    static func shortDay(_ date: Date) -> String {
        date.formatted(.dateTime.day().month(.abbreviated).locale(L10n.locale))
    }

    static func time(_ date: Date) -> String {
        date.formatted(.dateTime.hour().minute().locale(L10n.locale))
    }

    /// Parses "42,5" as well as "42.5".
    static func parse(_ text: String) -> Double? {
        let cleaned = text.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".")
        if cleaned.isEmpty { return nil }
        return Double(cleaned)
    }
}
