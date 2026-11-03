import PhotosUI
import SwiftData
import SwiftUI

/// Card on Today, right under "Start workout": tap to show the access code at the gate.
struct GymPassCard: View {
    @Query(sort: \GymPass.createdAt) private var passes: [GymPass]
    @State private var showing: GymPass?
    @State private var adding = false

    var body: some View {
        Group {
            if let pass = passes.first {
                Button {
                    showing = pass
                } label: {
                    HStack(spacing: 14) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 10, style: .continuous).fill(.white)
                            BarcodeView(value: pass.code, format: pass.format)
                                .padding(pass.format.isLinear ? 4 : 5)
                        }
                        .frame(width: pass.format.isLinear ? 92 : 56, height: 56)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(tr("My gym pass", "Mijn sportschoolpas"))
                                .font(.caption.weight(.heavy))
                                .textCase(.uppercase)
                                .foregroundStyle(Theme.accent)
                            Text(pass.gymName.isEmpty ? tr("Gym", "Sportschool") : pass.gymName)
                                .font(Theme.heading(19))
                                .textCase(.uppercase)
                                .foregroundStyle(.white)
                                .lineLimit(1)
                            Text(tr("Tap to show at the entrance", "Tik om te tonen bij de ingang"))
                                .font(.caption)
                                .foregroundStyle(Theme.muted)
                        }
                        Spacer()
                        Image(systemName: "qrcode.viewfinder")
                            .font(.title2)
                            .foregroundStyle(Theme.accent)
                    }
                    .liftrCard(padding: 14)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(tr("Show gym pass", "Toon sportschoolpas"))
            } else {
                Button {
                    adding = true
                } label: {
                    HStack(spacing: 14) {
                        Image(systemName: "qrcode")
                            .font(.title)
                            .foregroundStyle(Theme.accent)
                            .frame(width: 56, height: 56)
                            .background(Theme.accent.opacity(0.14), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        VStack(alignment: .leading, spacing: 3) {
                            Text(tr("Add your gym pass", "Voeg je sportschoolpas toe"))
                                .font(.headline)
                                .foregroundStyle(.white)
                            Text(tr("Show the QR code or barcode at the entrance, right from Liftr.",
                                    "Toon de QR- of barcode bij de ingang, gewoon vanuit Liftr."))
                                .font(.subheadline)
                                .foregroundStyle(Theme.muted)
                        }
                        Spacer()
                        Image(systemName: "plus.circle.fill")
                            .font(.title2)
                            .foregroundStyle(Theme.accent)
                    }
                    .liftrCard(padding: 14)
                }
                .buttonStyle(.plain)
            }
        }
        .fullScreenCover(item: $showing) { pass in
            GymPassFullScreen(pass: pass)
        }
        .sheet(isPresented: $adding) { GymPassEditView(pass: nil) }
    }
}

/// White screen with the code as large as possible and the screen at full brightness.
struct GymPassFullScreen: View {
    let pass: GymPass
    @Environment(\.dismiss) private var dismiss
    @State private var editing = false
    @State private var savedBrightness: CGFloat?

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()
            VStack(spacing: 22) {
                HStack {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.headline.weight(.bold))
                            .foregroundStyle(.black)
                            .frame(width: 44, height: 44)
                            .background(Color.black.opacity(0.06), in: Circle())
                    }
                    .accessibilityLabel(tr("Close", "Sluiten"))
                    Spacer()
                    Button(tr("Edit", "Wijzig")) { editing = true }
                        .font(.headline)
                        .foregroundStyle(Theme.accentDeep)
                }
                Spacer()
                VStack(spacing: 6) {
                    Text(pass.gymName.isEmpty ? tr("Gym", "Sportschool") : pass.gymName)
                        .font(Theme.display(34))
                        .textCase(.uppercase)
                        .foregroundStyle(.black)
                        .multilineTextAlignment(.center)
                    if !pass.memberName.isEmpty {
                        Text(pass.memberName)
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(.black.opacity(0.6))
                    }
                }
                BarcodeView(value: pass.code, format: pass.format)
                    .frame(maxWidth: pass.format.isLinear ? 520 : 320)
                    .frame(height: pass.format.isLinear ? 160 : 320)
                    .padding(.horizontal, pass.format.isLinear ? 0 : 20)
                Text(pass.code)
                    .font(.system(.title3, design: .monospaced).weight(.semibold))
                    .foregroundStyle(.black.opacity(0.75))
                    .lineLimit(2)
                    .minimumScaleFactor(0.5)
                    .textSelection(.enabled)
                if !pass.note.isEmpty {
                    Text(pass.note)
                        .font(.subheadline)
                        .foregroundStyle(.black.opacity(0.55))
                        .multilineTextAlignment(.center)
                }
                Spacer()
                Label(tr("Screen brightness is turned up", "Schermhelderheid staat op maximaal"), systemImage: "sun.max.fill")
                    .font(.caption)
                    .foregroundStyle(.black.opacity(0.4))
            }
            .padding(24)
        }
        .preferredColorScheme(.light)
        .statusBarHidden()
        .onAppear { brighten() }
        .onDisappear { restore() }
        .sheet(isPresented: $editing) { GymPassEditView(pass: pass, onDelete: { dismiss() }) }
    }

    private func brighten() {
        guard let screen = UIApplication.shared.connectedScenes.compactMap({ ($0 as? UIWindowScene)?.screen }).first else { return }
        if savedBrightness == nil { savedBrightness = screen.brightness }
        screen.brightness = 1
    }

    private func restore() {
        guard let saved = savedBrightness,
              let screen = UIApplication.shared.connectedScenes.compactMap({ ($0 as? UIWindowScene)?.screen }).first else { return }
        screen.brightness = saved
        savedBrightness = nil
    }
}

/// Add or edit the pass: scan it, read it from a screenshot, or type the number.
struct GymPassEditView: View {
    let pass: GymPass?
    var onDelete: (() -> Void)? = nil

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @State private var gymName = ""
    @State private var memberName = ""
    @State private var code = ""
    @State private var format: BarcodeFormat = .qr
    @State private var note = ""
    @State private var loaded = false
    @State private var scanning = false
    @State private var photo: PhotosPickerItem?
    @State private var photoError = false
    @State private var confirmDelete = false

    private var trimmedCode: String { code.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Button {
                        scanning = true
                    } label: {
                        Label(tr("Scan with camera", "Scan met de camera"), systemImage: "camera.viewfinder")
                    }
                    PhotosPicker(selection: $photo, matching: .images) {
                        Label(tr("Choose a screenshot or photo", "Kies een screenshot of foto"), systemImage: "photo")
                    }
                } header: {
                    Text(tr("Code", "Code"))
                } footer: {
                    Text(tr("Scan the card or key fob, or take a screenshot of the code in your gym's app and choose it here.",
                            "Scan je pasje of sleutelhanger, of maak een screenshot van de code in de app van je sportschool en kies hem hier."))
                }
                .listRowBackground(Theme.card)

                Section {
                    TextField(tr("Code or member number", "Code of lidnummer"), text: $code)
                        .font(.body.monospaced())
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                    Picker(tr("Type", "Soort"), selection: $format) {
                        ForEach(BarcodeFormat.allCases) { Text($0.displayName).tag($0) }
                    }
                    if !trimmedCode.isEmpty {
                        BarcodeView(value: trimmedCode, format: format)
                            .frame(height: format.isLinear ? 90 : 160)
                            .padding(12)
                            .frame(maxWidth: .infinity)
                            .background(.white, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                    }
                }
                .listRowBackground(Theme.card)

                Section(tr("Details", "Gegevens")) {
                    TextField(tr("Gym, e.g. my local gym", "Sportschool, bijv. club in Apeldoorn"), text: $gymName)
                    TextField(tr("Name on the pass (optional)", "Naam op de pas (optioneel)"), text: $memberName)
                    TextField(tr("Note (optional)", "Notitie (optioneel)"), text: $note, axis: .vertical)
                }
                .listRowBackground(Theme.card)

                if pass != nil {
                    Section {
                        Button(tr("Delete pass", "Pas verwijderen"), role: .destructive) { confirmDelete = true }
                    }
                    .listRowBackground(Theme.card)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(pass == nil ? tr("Gym pass", "Sportschoolpas") : tr("Edit pass", "Pas wijzigen"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(tr("Cancel", "Annuleren")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(tr("Save", "Bewaar")) { save() }
                        .fontWeight(.bold)
                        .disabled(trimmedCode.isEmpty)
                }
            }
            .sheet(isPresented: $scanning) {
                ScannerSheet { value, scanned in
                    code = value
                    format = scanned
                }
            }
            .onChange(of: photo) { _, item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self),
                       let result = await ImageCodeReader.read(data) {
                        code = result.payload
                        format = result.format
                    } else {
                        photoError = true
                    }
                    photo = nil
                }
            }
            .alert(tr("No code found", "Geen code gevonden"), isPresented: $photoError) {
                Button("OK") {}
            } message: {
                Text(tr("Liftr couldn't find a QR code or barcode in this picture.",
                        "Liftr vond geen QR- of barcode in deze afbeelding."))
            }
            .confirmationDialog(tr("Delete this pass?", "Pas verwijderen?"), isPresented: $confirmDelete, titleVisibility: .visible) {
                Button(tr("Delete", "Verwijderen"), role: .destructive) {
                    let target = pass
                    dismiss()
                    onDelete?()
                    Task { @MainActor in
                        try? await Task.sleep(for: .milliseconds(450))
                        if let target { context.delete(target) }
                        try? context.save()
                    }
                }
            }
            .onAppear {
                guard !loaded else { return }
                loaded = true
                if let pass {
                    gymName = pass.gymName
                    memberName = pass.memberName
                    code = pass.code
                    format = pass.format
                    note = pass.note
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private func save() {
        let target = pass ?? {
            let new = GymPass(gymName: "", code: "", format: format)
            context.insert(new)
            return new
        }()
        target.gymName = gymName.trimmingCharacters(in: .whitespaces)
        target.memberName = memberName.trimmingCharacters(in: .whitespaces)
        target.code = trimmedCode
        target.format = format
        target.note = note
        target.refreshCodeImage()
        try? context.save()
        dismiss()
    }
}
