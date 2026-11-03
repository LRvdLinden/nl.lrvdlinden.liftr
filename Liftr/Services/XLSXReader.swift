import Compression
import Foundation

/// Reads the cell text of an Excel workbook (.xlsx) without third-party code:
/// an .xlsx file is a zip archive of XML files.
enum XLSXReader {
    struct Sheet {
        var name: String
        /// Rows of cell text; empty cells are "".
        var rows: [[String]]
    }

    enum ReadError: Error { case notAZip, missing(String) }

    static func sheets(from data: Data) throws -> [Sheet] {
        let files = try unzip(data)
        func file(_ path: String) -> Data? {
            files[path] ?? files.first { $0.key.lowercased() == path.lowercased() }?.value
        }
        let shared = file("xl/sharedStrings.xml").map(SharedStringsParser.parse) ?? []

        // Sheet names and their files, in workbook order.
        var list: [(name: String, path: String)] = []
        if let workbook = file("xl/workbook.xml") {
            let rels = file("xl/_rels/workbook.xml.rels").map(RelationshipsParser.parse) ?? [:]
            for sheet in WorkbookParser.parse(workbook) {
                guard var target = rels[sheet.relID] else { continue }
                if target.hasPrefix("/") { target.removeFirst() } else { target = "xl/" + target }
                list.append((sheet.name, target))
            }
        }
        if list.isEmpty {
            list = files.keys.filter { $0.hasPrefix("xl/worksheets/") && $0.hasSuffix(".xml") }.sorted()
                .map { ($0.components(separatedBy: "/").last ?? $0, $0) }
        }
        guard !list.isEmpty else { throw ReadError.missing("worksheets") }
        return list.compactMap { entry in
            guard let xml = file(entry.path) else { return nil }
            return Sheet(name: entry.name, rows: SheetParser.parse(xml, shared: shared))
        }
    }

    // MARK: Zip

    private static func u16(_ d: Data, _ o: Int) -> Int {
        guard o + 2 <= d.count else { return 0 }
        let b = d.startIndex + o
        return Int(d[b]) | Int(d[b + 1]) << 8
    }

    private static func u32(_ d: Data, _ o: Int) -> Int {
        guard o + 4 <= d.count else { return 0 }
        return u16(d, o) | u16(d, o + 2) << 16
    }

    static func unzip(_ data: Data) throws -> [String: Data] {
        // End of central directory record: within the last 64 KB.
        var eocd = -1
        var i = data.count - 22
        let lowest = max(0, data.count - 65_557)
        while i >= lowest {
            if u32(data, i) == 0x0605_4B50 { eocd = i; break }
            i -= 1
        }
        guard eocd >= 0 else { throw ReadError.notAZip }
        let count = u16(data, eocd + 10)
        var offset = u32(data, eocd + 16)
        var files: [String: Data] = [:]

        for _ in 0..<count {
            guard u32(data, offset) == 0x0201_4B50 else { break }
            let method = u16(data, offset + 10)
            let compressed = u32(data, offset + 20)
            let size = u32(data, offset + 24)
            let nameLength = u16(data, offset + 28)
            let extraLength = u16(data, offset + 30)
            let commentLength = u16(data, offset + 32)
            let local = u32(data, offset + 42)
            let nameStart = data.startIndex + offset + 46
            let name = String(decoding: data[nameStart..<min(data.endIndex, nameStart + nameLength)], as: UTF8.self)
            offset += 46 + nameLength + extraLength + commentLength

            guard name.hasSuffix(".xml") || name.hasSuffix(".rels"), u32(data, local) == 0x0403_4B50 else { continue }
            let start = local + 30 + u16(data, local + 26) + u16(data, local + 28)
            guard start + compressed <= data.count else { continue }
            let raw = data.subdata(in: (data.startIndex + start)..<(data.startIndex + start + compressed))
            switch method {
            case 0: files[name] = raw
            case 8: if let inflated = inflate(raw, size: size) { files[name] = inflated }
            default: continue
            }
        }
        return files
    }

    /// Raw deflate (zip method 8) with Apple's Compression framework.
    private static func inflate(_ data: Data, size: Int) -> Data? {
        guard size > 0, size < 200_000_000 else { return size == 0 ? Data() : nil }
        var output = Data(count: size)
        let written = output.withUnsafeMutableBytes { (out: UnsafeMutableRawBufferPointer) -> Int in
            data.withUnsafeBytes { (input: UnsafeRawBufferPointer) -> Int in
                guard let dst = out.bindMemory(to: UInt8.self).baseAddress,
                      let src = input.bindMemory(to: UInt8.self).baseAddress else { return 0 }
                return compression_decode_buffer(dst, size, src, data.count, nil, COMPRESSION_ZLIB)
            }
        }
        guard written > 0 else { return nil }
        output.count = written
        return output
    }

    // MARK: XML

    private final class SharedStringsParser: NSObject, XMLParserDelegate {
        var strings: [String] = []
        private var current = ""
        private var inText = false
        private var inPhonetic = false

        static func parse(_ data: Data) -> [String] {
            let delegate = SharedStringsParser()
            let parser = XMLParser(data: data)
            parser.delegate = delegate
            parser.parse()
            return delegate.strings
        }

        func parser(_ parser: XMLParser, didStartElement name: String, namespaceURI: String?, qualifiedName: String?, attributes: [String: String] = [:]) {
            switch XLSXReader.local(name) {
            case "si": current = ""
            case "t": inText = !inPhonetic
            case "rPh": inPhonetic = true
            default: break
            }
        }

        func parser(_ parser: XMLParser, foundCharacters string: String) {
            if inText { current += string }
        }

        func parser(_ parser: XMLParser, didEndElement name: String, namespaceURI: String?, qualifiedName: String?) {
            switch XLSXReader.local(name) {
            case "si": strings.append(current)
            case "t": inText = false
            case "rPh": inPhonetic = false
            default: break
            }
        }
    }

    private final class RelationshipsParser: NSObject, XMLParserDelegate {
        var map: [String: String] = [:]

        static func parse(_ data: Data) -> [String: String] {
            let delegate = RelationshipsParser()
            let parser = XMLParser(data: data)
            parser.delegate = delegate
            parser.parse()
            return delegate.map
        }

        func parser(_ parser: XMLParser, didStartElement name: String, namespaceURI: String?, qualifiedName: String?, attributes: [String: String] = [:]) {
            if XLSXReader.local(name) == "Relationship", let id = attributes["Id"], let target = attributes["Target"] { map[id] = target }
        }
    }

    private final class WorkbookParser: NSObject, XMLParserDelegate {
        var sheets: [(name: String, relID: String)] = []

        static func parse(_ data: Data) -> [(name: String, relID: String)] {
            let delegate = WorkbookParser()
            let parser = XMLParser(data: data)
            parser.delegate = delegate
            parser.parse()
            return delegate.sheets
        }

        func parser(_ parser: XMLParser, didStartElement name: String, namespaceURI: String?, qualifiedName: String?, attributes: [String: String] = [:]) {
            guard XLSXReader.local(name) == "sheet", let sheetName = attributes["name"] else { return }
            let rel = attributes["r:id"] ?? attributes.first { $0.key.hasSuffix(":id") }?.value ?? ""
            sheets.append((sheetName, rel))
        }
    }

    private final class SheetParser: NSObject, XMLParserDelegate {
        let shared: [String]
        var cells: [Int: [Int: String]] = [:]
        private var row = 0
        private var column = 0
        private var type = ""
        private var value = ""
        private var collecting = false

        init(shared: [String]) { self.shared = shared }

        static func parse(_ data: Data, shared: [String]) -> [[String]] {
            let delegate = SheetParser(shared: shared)
            let parser = XMLParser(data: data)
            parser.delegate = delegate
            parser.parse()
            guard let maxRow = delegate.cells.keys.max() else { return [] }
            let maxColumn = delegate.cells.values.flatMap(\.keys).max() ?? 0
            return (0...maxRow).map { r in (0...maxColumn).map { c in delegate.cells[r]?[c] ?? "" } }
        }

        func parser(_ parser: XMLParser, didStartElement name: String, namespaceURI: String?, qualifiedName: String?, attributes: [String: String] = [:]) {
            switch XLSXReader.local(name) {
            case "row":
                if let r = attributes["r"].flatMap(Int.init) { row = r - 1 } else { row += 1 }
                column = -1
            case "c":
                if let ref = attributes["r"] { (row, column) = Self.position(ref, fallbackRow: row) } else { column += 1 }
                type = attributes["t"] ?? ""
                value = ""
            case "v", "t":
                collecting = true
            default: break
            }
        }

        func parser(_ parser: XMLParser, foundCharacters string: String) {
            if collecting { value += string }
        }

        func parser(_ parser: XMLParser, didEndElement name: String, namespaceURI: String?, qualifiedName: String?) {
            switch XLSXReader.local(name) {
            case "v", "t":
                collecting = false
            case "c":
                var text = value
                if type == "s", let index = Int(value), shared.indices.contains(index) { text = shared[index] }
                if type == "b" { text = value == "1" ? "TRUE" : "FALSE" }
                text = text.trimmingCharacters(in: .whitespacesAndNewlines)
                if !text.isEmpty { cells[row, default: [:]][column] = text }
            default: break
            }
        }

        /// "AB12" → (row 11, column 27).
        static func position(_ ref: String, fallbackRow: Int) -> (Int, Int) {
            var column = 0
            var digits = ""
            for ch in ref.uppercased() {
                if let a = ch.asciiValue, (65...90).contains(a) {
                    column = column * 26 + Int(a - 64)
                } else if ch.isNumber {
                    digits.append(ch)
                }
            }
            return ((Int(digits) ?? fallbackRow + 1) - 1, max(0, column - 1))
        }
    }

    static func local(_ name: String) -> String {
        name.split(separator: ":").last.map(String.init) ?? name
    }
}
