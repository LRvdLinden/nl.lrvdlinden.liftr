import Foundation
import UIKit
import Vision

/// Reads a barcode or QR code from a photo or screenshot, entirely on the device (from Pasr).
enum ImageCodeReader {
    static func read(_ data: Data) async -> (payload: String, format: BarcodeFormat)? {
        guard let cgImage = UIImage(data: data)?.cgImage else { return nil }
        return await Task.detached(priority: .userInitiated) { () -> (payload: String, format: BarcodeFormat)? in
            let request = VNDetectBarcodesRequest()
            let handler = VNImageRequestHandler(cgImage: cgImage)
            try? handler.perform([request])
            let best = (request.results ?? [])
                .filter { $0.payloadStringValue?.isEmpty == false }
                .max { $0.boundingBox.width * $0.boundingBox.height < $1.boundingBox.width * $1.boundingBox.height }
            guard let best, let payload = best.payloadStringValue else { return nil }
            return (payload, format(for: best.symbology, payload: payload))
        }.value
    }

    static func format(for symbology: VNBarcodeSymbology, payload: String) -> BarcodeFormat {
        switch symbology {
        case .ean13: return payload.count == 13 && payload.hasPrefix("0") ? .upcA : .ean13
        case .ean8: return .ean8
        case .upce: return .code128
        case .code128, .gs1DataBar, .gs1DataBarExpanded, .gs1DataBarLimited: return .code128
        case .code39, .code39Checksum, .code39FullASCII, .code39FullASCIIChecksum, .code93, .code93i: return .code39
        case .itf14, .i2of5, .i2of5Checksum: return .itf
        case .qr, .microQR: return .qr
        case .pdf417, .microPDF417: return .pdf417
        case .aztec: return .aztec
        default: return BarcodeFormat.guess(for: payload)
        }
    }
}
