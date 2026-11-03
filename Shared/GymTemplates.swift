import SwiftUI
import UIKit

/// A known gym chain: name, website (logo file), card colour and the code type its pass uses.
/// Same data as Pasr's "Sport en fitness" stores; logos live in Shared/GymLogos/<domain>.png.
struct GymTemplate: Identifiable, Hashable {
    let id: String
    let name: String
    let domain: String
    let colorHex: String
    let format: BarcodeFormat
    /// Logo is artwork that fills its square (show as a rounded icon instead of a see-through mark).
    var opaqueLogo = false
}

enum GymTemplates {
    static let all: [GymTemplate] = [
        .init(id: "basicfit", name: "Basic-Fit", domain: "basic-fit.com", colorHex: "#FB7819", format: .qr),
        .init(id: "sportcity", name: "SportCity", domain: "sportcity.nl", colorHex: "#E30613", format: .qr, opaqueLogo: true),
        .init(id: "anytime", name: "Anytime Fitness", domain: "anytimefitness.nl", colorHex: "#5E2D91", format: .code128),
        .init(id: "trainmore", name: "TrainMore", domain: "trainmore.nl", colorHex: "#E30613", format: .qr),
        .init(id: "fitforfree", name: "Fit For Free", domain: "fitforfree.nl", colorHex: "#E30613", format: .qr, opaqueLogo: true),
        .init(id: "snapfitness", name: "Snap Fitness", domain: "snapfitness.com", colorHex: "#6A1B9A", format: .qr),
        .init(id: "biggym", name: "Big Gym", domain: "biggym.nl", colorHex: "#1A1A1A", format: .qr, opaqueLogo: true),
        .init(id: "fit20", name: "fit20", domain: "fit20.nl", colorHex: "#E30613", format: .qr),
        .init(id: "healthcity", name: "HealthCity", domain: "healthcity.nl", colorHex: "#E30613", format: .qr),
    ]

    static func template(id: String?) -> GymTemplate? {
        guard let id else { return nil }
        return all.first { $0.id == id }
    }

    /// The logo that ships with the app for a gym, as PNG data.
    static func logo(for template: GymTemplate?) -> UIImage? {
        guard let domain = template?.domain, !domain.isEmpty,
              let url = Bundle.main.url(forResource: domain, withExtension: "png")
                ?? Bundle.main.url(forResource: domain, withExtension: "png", subdirectory: "GymLogos"),
              let data = try? Data(contentsOf: url) else { return nil }
        return UIImage(data: data)
    }
}

extension Color {
    /// "#FB7819" → colour (falls back to Liftr orange).
    init(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("#") { s.removeFirst() }
        let v = UInt32(s, radix: 16) ?? 0xFF6B00
        self.init(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
    }

    /// White or black, whichever reads best on this card colour.
    static func ink(on hex: String) -> Color {
        var s = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("#") { s.removeFirst() }
        let v = UInt32(s, radix: 16) ?? 0xFF6B00
        let r = Double((v >> 16) & 0xFF) / 255, g = Double((v >> 8) & 0xFF) / 255, b = Double(v & 0xFF) / 255
        let luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return luminance > 0.62 ? .black : .white
    }
}

extension GymPass {
    var template: GymTemplate? { GymTemplates.template(id: templateID) }

    /// The name on the card: the chain's name, or what the user typed.
    var displayName: String {
        if let template, gymName.isEmpty { return template.name }
        return gymName.isEmpty ? tr("Gym", "Sportschool") : gymName
    }

    /// "8 7192 4402 1187" — numbers in groups of four, as printed on most passes.
    var groupedCode: String {
        let compact = code.replacingOccurrences(of: " ", with: "")
        guard compact.count > 8, compact.count <= 24, compact.allSatisfy(\.isNumber) else { return code }
        return stride(from: 0, to: compact.count, by: 4).map { start in
            let s = compact.index(compact.startIndex, offsetBy: start)
            let e = compact.index(s, offsetBy: min(4, compact.count - start))
            return String(compact[s..<e])
        }.joined(separator: " ")
    }
}
