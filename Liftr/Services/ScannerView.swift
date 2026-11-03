import SwiftUI
import Vision
import VisionKit

/// Live camera scanner (from Pasr). Calls `onScan` once with the first code it sees.
struct ScannerView: UIViewControllerRepresentable {
    var onScan: (String, BarcodeFormat) -> Void

    static var isAvailable: Bool { DataScannerViewController.isSupported && DataScannerViewController.isAvailable }

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let controller = DataScannerViewController(
            recognizedDataTypes: [.barcode()],
            qualityLevel: .accurate,
            recognizesMultipleItems: false,
            isHighFrameRateTrackingEnabled: true,
            isHighlightingEnabled: true
        )
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: DataScannerViewController, context: Context) {
        if !controller.isScanning { try? controller.startScanning() }
    }

    static func dismantleUIViewController(_ controller: DataScannerViewController, coordinator: Coordinator) {
        controller.stopScanning()
    }

    func makeCoordinator() -> Coordinator { Coordinator(onScan: onScan) }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let onScan: (String, BarcodeFormat) -> Void
        private var done = false

        init(onScan: @escaping (String, BarcodeFormat) -> Void) { self.onScan = onScan }

        func dataScanner(_ scanner: DataScannerViewController, didAdd items: [RecognizedItem],
                         allItems: [RecognizedItem]) {
            guard !done else { return }
            for item in items {
                if case .barcode(let barcode) = item, let payload = barcode.payloadStringValue, !payload.isEmpty {
                    done = true
                    let format = ImageCodeReader.format(for: barcode.observation.symbology, payload: payload)
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    onScan(payload, format)
                    return
                }
            }
        }
    }
}

/// The scanner in a sheet with a hint and a cancel button.
struct ScannerSheet: View {
    var onScan: (String, BarcodeFormat) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if ScannerView.isAvailable {
                    ScannerView { value, format in
                        onScan(value, format)
                        dismiss()
                    }
                    .ignoresSafeArea()
                    .overlay(alignment: .bottom) {
                        Text(tr("Point the camera at the code on your pass", "Richt de camera op de code van je pas"))
                            .font(.subheadline.weight(.medium))
                            .padding(.horizontal, 16).padding(.vertical, 10)
                            .background(.ultraThinMaterial, in: Capsule())
                            .padding(.bottom, 40)
                    }
                } else {
                    ContentUnavailableView(tr("Camera not available", "Camera niet beschikbaar"),
                                           systemImage: "camera.metering.unknown",
                                           description: Text(tr("Allow camera access in Settings, or choose a screenshot of your pass.",
                                                                "Geef toegang tot de camera in Instellingen, of kies een screenshot van je pas.")))
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(tr("Cancel", "Annuleren")) { dismiss() }
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}
