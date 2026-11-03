import SwiftData
import SwiftUI
import UIKit

/// A known gym chain: name, website, card colour and the code type its pass uses.
/// The app ships a logo for most chains (Shared/GymLogos/<domain>.png); on iPhone the official
/// icon from the chain's own website replaces it once downloaded (GymLogoService).
struct GymTemplate: Identifiable, Hashable {
    enum LogoStyle: Hashable {
        /// Square artwork: shown as a rounded icon.
        case icon
        /// Wide wordmark: shown on a pill.
        case wordmark
    }

    let id: String
    let name: String
    let domain: String
    /// The chain's house colour, used for the card.
    let colorHex: String
    let format: BarcodeFormat
    var logoStyle: LogoStyle = .icon
    /// Tile behind a see-through logo so it always reads on the card (nil: logo is opaque artwork).
    var logoBackgroundHex: String? = nil
    /// Kept so existing passes still find their chain, but no longer offered in the picker.
    var retired = false
    /// Card colours earlier builds gave this chain; passes still on one of these move to the current colour.
    var formerColors: [String] = []
}

enum GymTemplates {
    static let all: [GymTemplate] = [
        .init(id: "basicfit", name: "Basic-Fit", domain: "basic-fit.com", colorHex: "#FB7819", format: .qr,
              logoStyle: .wordmark),
        .init(id: "sportcity", name: "SportCity", domain: "sportcity.nl", colorHex: "#F7B72C", format: .qr,
              formerColors: ["#E30613"]),
        .init(id: "anytime", name: "Anytime Fitness", domain: "anytimefitness.nl", colorHex: "#5E2D91", format: .code128,
              logoBackgroundHex: "#5E2D91"),
        .init(id: "trainmore", name: "TrainMore", domain: "trainmore.nl", colorHex: "#111111", format: .qr,
              formerColors: ["#E30613"]),
        .init(id: "snapfitness", name: "Snap Fitness", domain: "snapfitness.com", colorHex: "#B3302A", format: .qr,
              logoBackgroundHex: "#FFFFFF", formerColors: ["#6A1B9A"]),
        .init(id: "biggym", name: "Big Gym", domain: "biggym.nl", colorHex: "#111111", format: .qr,
              formerColors: ["#1A1A1A"]),
        .init(id: "fit20", name: "fit20", domain: "fit20.nl", colorHex: "#8DC21F", format: .qr,
              logoStyle: .wordmark, logoBackgroundHex: "#FFFFFF", formerColors: ["#E30613"]),
        .init(id: "healthcity", name: "HealthCity", domain: "healthcity.nl", colorHex: "#1A1A1A", format: .qr,
              logoBackgroundHex: "#FFFFFF", formerColors: ["#E30613"]),
        // fitforfree.nl now serves SportCity's logo and site, so it is no longer offered as a separate chain.
        .init(id: "fitforfree", name: "Fit For Free", domain: "fitforfree.nl", colorHex: "#F7B72C", format: .qr,
              retired: true, formerColors: ["#E30613"]),
    ]

    /// The chains offered in the picker.
    static var selectable: [GymTemplate] { all.filter { !$0.retired } }

    static func template(id: String?) -> GymTemplate? {
        guard let id else { return nil }
        return all.first { $0.id == id }
    }

    /// The logo that ships with the app for a gym.
    static func bundledLogo(for template: GymTemplate?) -> UIImage? {
        guard let template, !template.retired, !template.domain.isEmpty,
              let url = Bundle.main.url(forResource: template.domain, withExtension: "png")
                ?? Bundle.main.url(forResource: template.domain, withExtension: "png", subdirectory: "GymLogos"),
              let data = try? Data(contentsOf: url) else { return nil }
        return UIImage(data: data)
    }

    /// Where the official icon downloaded from the chain's website is kept.
    static func downloadedLogoURL(domain: String) -> URL {
        let folder = URL.cachesDirectory.appending(path: "GymLogos", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder.appending(path: domain + ".png")
    }

    /// The official website icon if it was downloaded, otherwise the logo in the app.
    static func logo(for template: GymTemplate?) -> (image: UIImage, fromWebsite: Bool)? {
        guard let template, !template.retired else { return nil }
        if let data = try? Data(contentsOf: downloadedLogoURL(domain: template.domain)), let image = UIImage(data: data) {
            return (image, true)
        }
        return bundledLogo(for: template).map { ($0, false) }
    }

    /// Passes still on a colour an earlier build gave their chain get the chain's current colour.
    @MainActor
    static func updateCardColors(in context: ModelContext) {
        let passes = (try? context.fetch(FetchDescriptor<GymPass>())) ?? []
        var changed = false
        for pass in passes {
            guard let template = pass.template,
                  template.formerColors.contains(where: { $0.caseInsensitiveCompare(pass.colorHex) == .orderedSame })
            else { continue }
            pass.colorHex = template.colorHex
            changed = true
        }
        if changed { try? context.save() }
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
