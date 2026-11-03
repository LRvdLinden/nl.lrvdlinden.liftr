import PhotosUI
import SwiftData
import SwiftUI

// MARK: - Card faces (like Pasr: flat brand colour, logo, flips to the code)

/// What a pass looks like; a value type so the editor can preview unsaved changes.
struct GymPassLook: Equatable {
    var name: String
    var templateID: String?
    var colorHex: String
    var code: String
    var format: BarcodeFormat
    var memberName: String

    init(name: String, templateID: String?, colorHex: String, code: String, format: BarcodeFormat, memberName: String) {
        self.name = name
        self.templateID = templateID
        self.colorHex = colorHex
        self.code = code
        self.format = format
        self.memberName = memberName
    }

    init(_ pass: GymPass) {
        self.init(name: pass.displayName, templateID: pass.templateID, colorHex: pass.colorHex,
                  code: pass.code, format: pass.format, memberName: pass.memberName)
    }

    var template: GymTemplate? { GymTemplates.template(id: templateID) }
    var ink: Color { Color.ink(on: colorHex) }

    var groupedCode: String {
        let compact = code.replacingOccurrences(of: " ", with: "")
        guard compact.count > 8, compact.count <= 24, compact.allSatisfy(\.isNumber) else { return code }
        return stride(from: 0, to: compact.count, by: 4).map { start in
            let s = compact.index(compact.startIndex, offsetBy: start)
            let e = compact.index(s, offsetBy: min(4, compact.count - start))
            return String(compact[s..<e])
        }.joined(separator: " ")
    }

    var monogram: String {
        name.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased()
    }
}

/// The gym's logo on its card: the chain's official icon as a rounded tile, a wordmark on a pill,
/// or a monogram for other gyms. See-through logos get a tile so they always read on the card colour.
struct GymLogoMark: View {
    let look: GymPassLook
    var height: CGFloat

    @State private var logo: (image: UIImage, fromWebsite: Bool)?

    init(look: GymPassLook, height: CGFloat) {
        self.look = look
        self.height = height
        _logo = State(initialValue: GymTemplates.logo(for: look.template))
    }

    var body: some View {
        content
            .task(id: look.templateID) {
                logo = GymTemplates.logo(for: look.template)
                if let template = look.template { await GymLogoService.refresh(template) }
            }
            .onReceive(NotificationCenter.default.publisher(for: GymLogoService.didUpdate)) { note in
                guard (note.object as? String) == look.template?.domain else { return }
                logo = GymTemplates.logo(for: look.template)
            }
    }

    @ViewBuilder private var content: some View {
        if let logo, let template = look.template {
            if logo.fromWebsite || template.logoStyle == .icon {
                // Website icons are square artwork; bundled square logos may be see-through.
                Image(uiImage: logo.image).resizable().scaledToFit()
                    .padding(logo.fromWebsite || template.logoBackgroundHex == nil ? 0 : height * 0.1)
                    .frame(width: height, height: height)
                    .background(tile(template, fromWebsite: logo.fromWebsite))
                    .clipShape(RoundedRectangle(cornerRadius: height * 0.22, style: .continuous))
            } else {
                Image(uiImage: logo.image).resizable().scaledToFit()
                    .padding(.horizontal, template.logoBackgroundHex == nil ? 0 : height * 0.22)
                    .padding(.vertical, template.logoBackgroundHex == nil ? 0 : height * 0.14)
                    .frame(maxWidth: height * 3.4, maxHeight: height, alignment: .leading)
                    .background(tile(template, fromWebsite: false))
                    .clipShape(RoundedRectangle(cornerRadius: height * 0.22, style: .continuous))
            }
        } else {
            Text(look.monogram.isEmpty ? "L" : look.monogram)
                .font(.system(size: height * 0.42, weight: .heavy, design: .rounded))
                .foregroundStyle(look.ink)
                .frame(width: height, height: height)
                .background(look.ink.opacity(0.16), in: RoundedRectangle(cornerRadius: height * 0.24, style: .continuous))
        }
    }

    private func tile(_ template: GymTemplate, fromWebsite: Bool) -> Color {
        if fromWebsite { return .white }
        return template.logoBackgroundHex.map { Color(hex: $0) } ?? .clear
    }
}

/// True when the logo already shows the gym's name, so the card does not repeat it.
private func logoShowsName(_ look: GymPassLook) -> Bool {
    guard let template = look.template, let logo = GymTemplates.logo(for: template) else { return false }
    return !logo.fromWebsite && template.logoStyle == .wordmark
}

/// Front: logo, gym name, member and number. Back (`showsCode`): the code on a white panel.
struct GymCardFace: View {
    let look: GymPassLook
    var showsCode = false
    var cornerRadius: CGFloat = 24
    var compact = false

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color(hex: look.colorHex)
            VStack(alignment: .leading, spacing: compact ? 8 : 12) {
                HStack(spacing: 10) {
                    GymLogoMark(look: look, height: compact ? 34 : 44)
                    if !logoShowsName(look) {
                        Text(look.name)
                            .font(.system(compact ? .headline : .title3, design: .rounded).weight(.bold))
                            .lineLimit(1)
                    }
                    Spacer(minLength: 4)
                    Text(tr("Gym pass", "Sportschoolpas"))
                        .font(.system(size: 10, weight: .semibold, design: .rounded))
                        .textCase(.uppercase)
                        .tracking(0.6)
                        .opacity(0.72)
                }
                if showsCode {
                    codePanel
                } else {
                    Spacer(minLength: 0)
                    HStack(alignment: .bottom) {
                        VStack(alignment: .leading, spacing: 2) {
                            if !look.memberName.isEmpty {
                                Text(look.memberName)
                                    .font(.system(.subheadline, design: .rounded).weight(.semibold))
                                    .lineLimit(1)
                            }
                            Text(look.groupedCode)
                                .font(.system(.subheadline, design: .monospaced).weight(.medium))
                                .lineLimit(1)
                                .minimumScaleFactor(0.6)
                                .opacity(0.9)
                        }
                        Spacer()
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(.footnote.weight(.semibold))
                            .opacity(0.6)
                    }
                }
            }
            .padding(compact ? 14 : 18)
        }
        .foregroundStyle(look.ink)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .shadow(color: .black.opacity(0.3), radius: 14, y: 8)
    }

    private var codePanel: some View {
        VStack(spacing: 8) {
            BarcodeView(value: look.code, format: look.format)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            Text(look.groupedCode)
                .font(.system(size: 14, weight: .semibold, design: .monospaced))
                .foregroundStyle(.black.opacity(0.75))
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .textSelection(.enabled)
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

/// Turns around its vertical axis: the front first, the code once `angle` passes 90° (from Pasr).
struct FlipGymCard: View, Animatable {
    let look: GymPassLook
    var angle: Double

    var animatableData: Double {
        get { angle }
        set { angle = newValue }
    }

    private var showingBack: Bool { angle.truncatingRemainder(dividingBy: 360) >= 90 }

    var body: some View {
        ZStack {
            GymCardFace(look: look, showsCode: false)
                .opacity(showingBack ? 0 : 1)
            GymCardFace(look: look, showsCode: true)
                .rotation3DEffect(.degrees(180), axis: (x: 0, y: 1, z: 0))
                .opacity(showingBack ? 1 : 0)
        }
        .rotation3DEffect(.degrees(angle), axis: (x: 0, y: 1, z: 0), perspective: 0.45)
    }
}

// MARK: - Today

/// Round button at the top right of Today, in the colour of the first pass (with the number of passes when
/// there are more). Tap: your passes, like Wallet. Without a pass it starts "Choose your gym".
struct GymPassButton: View {
    @Query(sort: \GymPass.createdAt) private var passes: [GymPass]
    @State private var showWallet = false
    @State private var adding = false

    var body: some View {
        Button {
            if passes.isEmpty { adding = true } else { showWallet = true }
        } label: {
            ZStack(alignment: .topTrailing) {
                if let first = passes.first {
                    Image(systemName: "wallet.pass.fill")
                        .font(.system(size: 21, weight: .semibold))
                        .foregroundStyle(Color.ink(on: first.colorHex))
                        .frame(width: 50, height: 50)
                        .background(Color(hex: first.colorHex), in: Circle())
                        .overlay(Circle().strokeBorder(.white.opacity(0.18), lineWidth: 1))
                        .shadow(color: Color(hex: first.colorHex).opacity(0.45), radius: 10, y: 4)
                } else {
                    Image(systemName: "wallet.pass")
                        .font(.system(size: 21, weight: .semibold))
                        .foregroundStyle(Theme.accent)
                        .frame(width: 50, height: 50)
                        .background(Theme.card, in: Circle())
                        .overlay(Circle().strokeBorder(Theme.accent.opacity(0.45), lineWidth: 1.5))
                }
                if passes.count > 1 {
                    Text("\(passes.count)")
                        .font(.caption2.weight(.heavy))
                        .foregroundStyle(.black)
                        .frame(minWidth: 18, minHeight: 18)
                        .background(.white, in: Circle())
                        .offset(x: 4, y: -4)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(passes.isEmpty ? tr("Add your gym pass", "Voeg je sportschoolpas toe") : tr("My passes", "Mijn passen"))
        .sheet(isPresented: $showWallet) { GymPassWalletView() }
        .sheet(isPresented: $adding) { GymPassEditView(pass: nil) }
    }
}

/// All passes as cards, like Wallet: tap one to show its code, long-press to edit or delete, + to add.
struct GymPassWalletView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Query(sort: \GymPass.createdAt) private var passes: [GymPass]
    @State private var showing: GymPass?
    @State private var editing: GymPass?
    @State private var adding = false
    @State private var deleting: GymPass?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    ForEach(passes) { pass in
                        Button {
                            showing = pass
                        } label: {
                            GymCardFace(look: GymPassLook(pass), compact: true)
                                .aspectRatio(1.75, contentMode: .fit)
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            Button { showing = pass } label: { Label(tr("Show code", "Toon code"), systemImage: "qrcode") }
                            Button { editing = pass } label: { Label(tr("Edit pass", "Pas wijzigen"), systemImage: "pencil") }
                            Button(role: .destructive) { deleting = pass } label: { Label(tr("Delete pass", "Pas verwijderen"), systemImage: "trash") }
                        }
                        .accessibilityLabel(trf("Show {1} pass", "Toon pas van {1}", pass.displayName))
                    }
                    Button {
                        adding = true
                    } label: {
                        Label(tr("Add pass", "Pas toevoegen"), systemImage: "plus")
                    }
                    .buttonStyle(LiftrButtonStyle(filled: passes.isEmpty))
                    .padding(.top, 6)
                }
                .padding(16)
                .frame(maxWidth: 560)
                .frame(maxWidth: .infinity)
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(tr("My passes", "Mijn passen"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(tr("Close", "Sluiten")) { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button { adding = true } label: { Image(systemName: "plus") }
                        .accessibilityLabel(tr("Add pass", "Pas toevoegen"))
                }
            }
            .fullScreenCover(item: $showing) { pass in GymPassFullScreen(pass: pass) }
            .sheet(item: $editing) { pass in GymPassEditView(pass: pass) }
            .sheet(isPresented: $adding) { GymPassEditView(pass: nil) }
            .confirmationDialog(tr("Delete this pass?", "Pas verwijderen?"),
                                isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
                                titleVisibility: .visible) {
                Button(tr("Delete", "Verwijderen"), role: .destructive) {
                    if let pass = deleting { context.delete(pass) }
                    try? context.save()
                    deleting = nil
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

/// The pass comes forward and turns to its code; screen brightness goes up. Tap the card to turn it back.
struct GymPassFullScreen: View {
    let pass: GymPass
    @Environment(\.dismiss) private var dismiss
    @State private var angle: Double = 0
    @State private var editing = false
    @State private var savedBrightness: CGFloat?

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            Color(hex: pass.colorHex).opacity(0.25).ignoresSafeArea()
            VStack(spacing: 20) {
                HStack {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.headline.weight(.bold))
                            .foregroundStyle(.white)
                            .frame(width: 44, height: 44)
                            .background(.white.opacity(0.14), in: Circle())
                    }
                    .accessibilityLabel(tr("Close", "Sluiten"))
                    Spacer()
                    Button(tr("Edit", "Wijzig")) { editing = true }
                        .font(.headline)
                        .foregroundStyle(.white)
                }
                Spacer()
                FlipGymCard(look: GymPassLook(pass), angle: angle)
                    .aspectRatio(0.82, contentMode: .fit)
                    .frame(maxWidth: 420)
                    .onTapGesture { withAnimation(.spring(response: 0.6, dampingFraction: 0.8)) { angle = angle >= 90 ? 0 : 180 } }
                    .accessibilityAddTraits(.isButton)
                    .accessibilityLabel(angle >= 90 ? tr("Show front", "Toon voorkant") : tr("Show code", "Toon code"))
                Text(angle >= 90 ? tr("Tap the card to turn it over", "Tik op de kaart om hem om te draaien")
                                 : tr("Tap the card to show the code", "Tik op de kaart om de code te tonen"))
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.6))
                Spacer()
                Label(tr("Screen brightness is turned up", "Schermhelderheid staat op maximaal"), systemImage: "sun.max.fill")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.5))
            }
            .padding(24)
        }
        .preferredColorScheme(.dark)
        .statusBarHidden()
        .onAppear {
            brighten()
            withAnimation(.spring(response: 0.7, dampingFraction: 0.8).delay(0.25)) { angle = 180 }
        }
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

// MARK: - Choose gym

/// Pick your gym chain (with logo and colour), or another gym.
struct GymPickerView: View {
    var onPick: (GymTemplate?) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var search = ""

    private var filtered: [GymTemplate] {
        search.isEmpty ? GymTemplates.selectable : GymTemplates.selectable.filter { $0.name.localizedCaseInsensitiveContains(search) }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 12)], spacing: 12) {
                    ForEach(filtered) { template in
                        Button {
                            onPick(template)
                            dismiss()
                        } label: {
                            let look = GymPassLook(name: template.name, templateID: template.id, colorHex: template.colorHex,
                                                   code: "", format: template.format, memberName: "")
                            VStack(alignment: .leading) {
                                GymLogoMark(look: look, height: 36)
                                Spacer(minLength: 8)
                                Text(template.name)
                                    .font(.system(.subheadline, design: .rounded).weight(.bold))
                                    .foregroundStyle(look.ink)
                                    .lineLimit(1)
                            }
                            .padding(14)
                            .frame(maxWidth: .infinity, minHeight: 96, alignment: .leading)
                            .background(Color(hex: template.colorHex), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                    Button {
                        onPick(nil)
                        dismiss()
                    } label: {
                        VStack(alignment: .leading) {
                            Image(systemName: "plus")
                                .font(.title2.weight(.bold))
                                .foregroundStyle(Theme.accent)
                            Spacer(minLength: 8)
                            Text(tr("Another gym", "Andere sportschool"))
                                .font(.system(.subheadline, design: .rounded).weight(.bold))
                                .foregroundStyle(.white)
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, minHeight: 96, alignment: .leading)
                        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Theme.line))
                    }
                    .buttonStyle(.plain)
                }
                .padding(16)
            }
            .background(Theme.background.ignoresSafeArea())
            .searchable(text: $search, prompt: tr("Search gym", "Zoek sportschool"))
            .navigationTitle(tr("Choose your gym", "Kies je sportschool"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(tr("Cancel", "Annuleren")) { dismiss() }
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

// MARK: - Add / edit

/// Add or edit the pass: choose the gym, then scan the card, read a screenshot, or type the number.
struct GymPassEditView: View {
    let pass: GymPass?
    var onDelete: (() -> Void)? = nil

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @State private var templateID: String?
    @State private var gymName = ""
    @State private var color: Color = Theme.accent
    @State private var colorHex = "#FF6B00"
    @State private var memberName = ""
    @State private var code = ""
    @State private var format: BarcodeFormat = .qr
    @State private var note = ""
    @State private var loaded = false
    @State private var picking = false
    @State private var scanning = false
    @State private var photo: PhotosPickerItem?
    @State private var photoError = false
    @State private var confirmDelete = false
    @State private var previewBack = false

    private var trimmedCode: String { code.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var template: GymTemplate? { GymTemplates.template(id: templateID) }

    private var look: GymPassLook {
        let name = gymName.trimmingCharacters(in: .whitespaces)
        return GymPassLook(name: name.isEmpty ? (template?.name ?? tr("Gym", "Sportschool")) : name,
                           templateID: templateID, colorHex: colorHex,
                           code: trimmedCode.isEmpty ? "0000 0000 0000" : trimmedCode, format: format, memberName: memberName)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    FlipGymCard(look: look, angle: previewBack && !trimmedCode.isEmpty ? 180 : 0)
                        .aspectRatio(previewBack ? 0.95 : 1.9, contentMode: .fit)
                        .animation(.spring(response: 0.5, dampingFraction: 0.85), value: previewBack)
                        .onTapGesture { if !trimmedCode.isEmpty { previewBack.toggle() } }
                        .listRowInsets(EdgeInsets(top: 12, leading: 16, bottom: 12, trailing: 16))
                        .listRowBackground(Color.clear)
                }

                Section(tr("Gym", "Sportschool")) {
                    Button {
                        picking = true
                    } label: {
                        HStack {
                            Text(template?.name ?? (gymName.isEmpty ? tr("Choose your gym", "Kies je sportschool") : tr("Another gym", "Andere sportschool")))
                                .foregroundStyle(.white)
                            Spacer()
                            Image(systemName: "chevron.right").foregroundStyle(Theme.muted)
                        }
                    }
                    if template == nil {
                        TextField(tr("Name of the gym", "Naam van de sportschool"), text: $gymName)
                        ColorPicker(tr("Card colour", "Kleur van de kaart"), selection: $color, supportsOpacity: false)
                    }
                }
                .listRowBackground(Theme.card)

                Section {
                    Button {
                        scanning = true
                    } label: {
                        Label(tr("Scan with camera", "Scan met de camera"), systemImage: "camera.viewfinder")
                    }
                    PhotosPicker(selection: $photo, matching: .images) {
                        Label(tr("Choose a screenshot or photo", "Kies een screenshot of foto"), systemImage: "photo")
                    }
                    TextField(tr("Code or member number", "Code of lidnummer"), text: $code)
                        .font(.body.monospaced())
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                    Picker(tr("Type", "Soort"), selection: $format) {
                        ForEach(BarcodeFormat.allCases) { Text($0.displayName).tag($0) }
                    }
                } header: {
                    Text(tr("Code", "Code"))
                } footer: {
                    Text(tr("Scan the card or key fob, or take a screenshot of the code in your gym's app and choose it here.",
                            "Scan je pasje of sleutelhanger, of maak een screenshot van de code in de app van je sportschool en kies hem hier."))
                }
                .listRowBackground(Theme.card)

                Section(tr("Details", "Gegevens")) {
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
            .sheet(isPresented: $picking) {
                GymPickerView { picked in
                    templateID = picked?.id
                    if let picked {
                        gymName = ""
                        colorHex = picked.colorHex
                        color = Color(hex: picked.colorHex)
                        if trimmedCode.isEmpty { format = picked.format }
                    }
                }
            }
            .sheet(isPresented: $scanning) {
                ScannerSheet { value, scanned in
                    code = value
                    format = scanned
                    previewBack = true
                }
            }
            .onChange(of: color) { _, new in
                if template == nil { colorHex = new.hexString }
            }
            .onChange(of: photo) { _, item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self),
                       let result = await ImageCodeReader.read(data) {
                        code = result.payload
                        format = result.format
                        previewBack = true
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
                    templateID = pass.templateID
                    gymName = pass.templateID == nil ? pass.gymName : ""
                    colorHex = pass.colorHex
                    color = Color(hex: pass.colorHex)
                    memberName = pass.memberName
                    code = pass.code
                    format = pass.format
                    note = pass.note
                } else {
                    picking = true
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
        target.templateID = templateID
        target.gymName = template == nil ? gymName.trimmingCharacters(in: .whitespaces) : ""
        target.colorHex = colorHex
        target.memberName = memberName.trimmingCharacters(in: .whitespaces)
        target.code = trimmedCode
        target.format = format
        target.note = note
        target.refreshCodeImage()
        try? context.save()
        dismiss()
    }
}

extension Color {
    /// "#RRGGBB" of this colour (sRGB).
    var hexString: String {
        let ui = UIColor(self)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        ui.getRed(&r, green: &g, blue: &b, alpha: &a)
        let clamp = { (v: CGFloat) in Int((max(0, min(1, v)) * 255).rounded()) }
        return String(format: "#%02X%02X%02X", clamp(r), clamp(g), clamp(b))
    }
}
