import Foundation
import UIKit

/// Downloads the official icon each gym chain publishes on its own website (apple-touch-icon or site icon),
/// so passes show the chain's current logo. Same approach as Pasr. Until it is downloaded, or offline,
/// the logo that ships with the app is shown.
enum GymLogoService {
    /// Posted with the domain as object when a new logo was saved.
    static let didUpdate = Notification.Name("GymLogoService.didUpdate")

    private static let checkedKey = "gymLogoChecked"
    @MainActor private static var inFlight: [String: Task<Data?, Never>] = [:]

    /// Fetches the logo when it was not checked in the last 14 days (or `force`).
    @MainActor
    static func refresh(_ template: GymTemplate, force: Bool = false) async {
        guard !template.retired, !template.domain.isEmpty else { return }
        var checked = UserDefaults.standard.dictionary(forKey: checkedKey) as? [String: Double] ?? [:]
        if !force, let last = checked[template.domain], Date.now.timeIntervalSince1970 - last < 14 * 86_400 { return }
        if let running = inFlight[template.domain] { _ = await running.value; return }

        let domain = template.domain
        let task = Task.detached(priority: .utility) { await download(domain: domain) }
        inFlight[domain] = task
        let data = await task.value
        inFlight[domain] = nil

        checked[domain] = Date.now.timeIntervalSince1970
        UserDefaults.standard.set(checked, forKey: checkedKey)
        guard let data else { return }
        let url = GymTemplates.downloadedLogoURL(domain: domain)
        if (try? Data(contentsOf: url)) != data {
            try? data.write(to: url, options: .atomic)
            NotificationCenter.default.post(name: didUpdate, object: domain)
        }
    }

    /// Refreshes every chain (in the background, at most every 14 days per chain).
    @MainActor
    static func refreshAll() async {
        for template in GymTemplates.selectable { await refresh(template) }
    }

    private static let session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 8
        config.httpAdditionalHeaders = [
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1",
            "Accept-Language": "nl-NL,nl;q=0.9,en;q=0.8",
        ]
        return URLSession(configuration: config)
    }()

    private static func download(domain: String) async -> Data? {
        var candidates: [URL] = []
        // 1. Icons the home page declares, apple-touch-icon and largest first.
        for host in ["https://www.\(domain)/", "https://\(domain)/"] {
            guard let url = URL(string: host),
                  case let (data, response)? = try? await session.data(from: url),
                  let html = String(data: data.prefix(800_000), encoding: .utf8)
                    ?? String(data: data.prefix(800_000), encoding: .isoLatin1)
            else { continue }
            candidates += iconLinks(in: html, base: response.url ?? url)
            break
        }
        // 2. The usual fixed place.
        candidates += ["https://www.\(domain)/apple-touch-icon.png", "https://\(domain)/apple-touch-icon.png"]
            .compactMap(URL.init(string:))

        var seen = Set<URL>()
        for url in candidates where seen.insert(url).inserted {
            guard case let (data, response)? = try? await session.data(from: url),
                  (response as? HTTPURLResponse)?.statusCode ?? 200 < 400,
                  data.count < 3_000_000,
                  let image = UIImage(data: data),
                  min(image.size.width * image.scale, image.size.height * image.scale) >= 96
            else { continue }
            return png(image, maxSide: 256)
        }
        return nil
    }

    private static func png(_ image: UIImage, maxSide: CGFloat) -> Data? {
        let w = image.size.width * image.scale, h = image.size.height * image.scale
        let factor = min(1, maxSide / max(w, h))
        let size = CGSize(width: (w * factor).rounded(), height: (h * factor).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }.pngData()
    }

    /// `<link rel="apple-touch-icon" …>` and `<link rel="icon" sizes="…">` from a page, best first.
    static func iconLinks(in html: String, base: URL) -> [URL] {
        guard let tagPattern = try? NSRegularExpression(pattern: "<link\\b[^>]*>", options: [.caseInsensitive]),
              let attrPattern = try? NSRegularExpression(
                pattern: "([a-zA-Z-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))") else { return [] }
        let ns = html as NSString
        var found: [(URL, Int)] = []
        for match in tagPattern.matches(in: html, range: NSRange(location: 0, length: ns.length)) {
            let tag = ns.substring(with: match.range)
            let tns = tag as NSString
            var attrs: [String: String] = [:]
            for a in attrPattern.matches(in: tag, range: NSRange(location: 0, length: tns.length)) {
                let key = tns.substring(with: a.range(at: 1)).lowercased()
                let value = (2...4).compactMap { i -> String? in
                    let r = a.range(at: i)
                    return r.location == NSNotFound ? nil : tns.substring(with: r)
                }.first ?? ""
                attrs[key] = value
            }
            let rel = attrs["rel"]?.lowercased() ?? ""
            guard rel.contains("icon"), !rel.contains("mask"), let href = attrs["href"],
                  !href.lowercased().hasSuffix(".svg"), !href.lowercased().hasSuffix(".ico"),
                  let url = URL(string: href.replacingOccurrences(of: "&amp;", with: "&"), relativeTo: base)?.absoluteURL
            else { continue }
            let declared = attrs["sizes"]?.lowercased().split(separator: "x").first.flatMap { Int($0) } ?? 0
            let score = (rel.contains("apple-touch-icon") ? 10_000 : 0) + declared
            found.append((url, score))
        }
        return found.sorted { $0.1 > $1.1 }.map(\.0)
    }
}
