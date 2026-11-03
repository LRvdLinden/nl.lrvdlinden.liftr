import HealthKit
import SwiftData
import SwiftUI

struct SettingsView: View {
    @Query private var workouts: [Workout]
    @AppStorage(WeightUnit.storageKey) private var unitRaw = "kg"
    @AppStorage("rest.default") private var restDefault = 90
    @AppStorage("rest.auto") private var restAuto = true
    @AppStorage("health.saveWorkouts") private var saveWorkouts = true
    @AppStorage("health.saveBody") private var saveBody = true
    @State private var health = HealthKitManager.shared
    @State private var cloud = CloudSyncStatus.shared
    @State private var showImport = false
    @State private var showBody = false
    @Environment(\.openURL) private var openURL
    @Environment(\.modelContext) private var context
    @AppStorage(ReminderScheduler.Key.daysOn) private var remindDays = false
    @AppStorage(ReminderScheduler.Key.days) private var remindDaysRaw = "2,4,6"
    @AppStorage(ReminderScheduler.Key.time) private var remindTime = 18 * 60 + 30
    @AppStorage(ReminderScheduler.Key.inactiveOn) private var remindInactive = false
    @AppStorage(ReminderScheduler.Key.inactiveDays) private var inactiveDays = 3
    @AppStorage(ReminderScheduler.Key.weeklyOn) private var remindWeekly = false
    @State private var notificationsDenied = false

    var body: some View {
        NavigationStack {
            List {
                Section(tr("Training", "Training")) {
                    Picker(tr("Unit", "Eenheid"), selection: $unitRaw) {
                        Text(tr("Kilograms (kg)", "Kilogram (kg)")).tag("kg")
                        Text(tr("Pounds (lb)", "Pond (lb)")).tag("lb")
                    }
                    Stepper(value: $restDefault, in: 15...600, step: 15) {
                        HStack {
                            Text(tr("Rest time", "Rusttijd"))
                            Spacer()
                            Text(LiftrFormat.duration(TimeInterval(restDefault)))
                                .monospacedDigit()
                                .foregroundStyle(Theme.accent)
                        }
                    }
                    Toggle(tr("Start rest timer after each set", "Rusttimer starten na elke set"), isOn: $restAuto)
                }
                .listRowBackground(Theme.card)

                remindersSection

                Section {
                    if !health.isAvailable {
                        Text(tr("Apple Health is not available on this device.", "Apple Gezondheid is niet beschikbaar op dit apparaat."))
                            .foregroundStyle(Theme.muted)
                    } else {
                        HStack {
                            Image(systemName: "heart.fill").foregroundStyle(.pink)
                            Text(health.canWriteWorkouts ? tr("Connected", "Gekoppeld") : tr("Not connected", "Niet gekoppeld"))
                            Spacer()
                            Button(health.hasRequested ? tr("Check access", "Toegang controleren") : tr("Connect", "Koppelen")) {
                                Task { await health.requestAuthorization() }
                            }
                            .foregroundStyle(Theme.accent)
                        }
                        Toggle(tr("Save workouts", "Trainingen opslaan"), isOn: $saveWorkouts)
                        Toggle(tr("Save weight and body fat", "Gewicht en vetpercentage opslaan"), isOn: $saveBody)
                        Button(tr("Log weight", "Gewicht invoeren")) { showBody = true }
                            .foregroundStyle(Theme.accent)
                    }
                } header: {
                    Text(tr("Apple Health", "Apple Gezondheid"))
                } footer: {
                    Text(tr("Liftr saves workouts with duration and calories and reads your weight, body composition and resting heart rate. Change access in the Health app → Profile → Apps → Liftr.",
                            "Liftr bewaart trainingen met duur en calorieën en leest je gewicht, lichaamssamenstelling en rusthartslag. Toegang wijzig je in de Gezondheid-app → Profiel → Apps → Liftr."))
                }
                .listRowBackground(Theme.card)

                Section {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: cloud.symbol)
                            .font(.title3)
                            .foregroundStyle(cloud.isHealthy ? Theme.success : .orange)
                            .symbolEffect(.pulse, isActive: cloud.state == .syncing || cloud.state == .checking)
                            .frame(width: 28)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(cloud.title)
                                .font(.body.weight(.semibold))
                            if !cloud.detail.isEmpty {
                                Text(cloud.detail)
                                    .font(.caption)
                                    .foregroundStyle(Theme.muted)
                            }
                        }
                    }
                    .padding(.vertical, 2)
                    .accessibilityElement(children: .combine)
                    if cloud.state == .noAccount {
                        Button(tr("Open Settings", "Open Instellingen")) {
                            if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                        }
                        .foregroundStyle(Theme.accent)
                    }
                    Button {
                        showImport = true
                    } label: {
                        Label(tr("Import from notes", "Importeren uit notities"), systemImage: "square.and.arrow.down")
                    }
                    .foregroundStyle(.white)
                    ShareLink(item: CSVExport(workouts: workouts),
                              preview: SharePreview(tr("Liftr export", "Liftr-export"), image: Image(systemName: "tablecells"))) {
                        Label(tr("Export to CSV", "Exporteren als CSV"), systemImage: "square.and.arrow.up")
                    }
                    .foregroundStyle(.white)
                } header: {
                    Text(tr("Data", "Gegevens"))
                } footer: {
                    Text(tr("Your workouts sync through your own iCloud between iPhone, iPad and Apple Watch.",
                            "Je trainingen synchroniseren via je eigen iCloud tussen iPhone, iPad en Apple Watch."))
                }
                .listRowBackground(Theme.card)

                Section {
                    NavigationLink {
                        PrivacyView()
                    } label: {
                        Label(tr("Privacy", "Privacy"), systemImage: "hand.raised.fill")
                    }
                }
                .listRowBackground(Theme.card)

                Section(MoreAppsText.current.section) {
                    MoreAppsCard()
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)

                Section {
                    AppInfoFooter()
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(tr("Settings", "Instellingen"))
            .sheet(isPresented: $showImport) { ImportView() }
            .task { await cloud.refresh() }
            .onChange(of: remindDays) { _, on in reminderChanged(turnedOn: on) }
            .onChange(of: remindInactive) { _, on in reminderChanged(turnedOn: on) }
            .onChange(of: remindWeekly) { _, on in reminderChanged(turnedOn: on) }
            .onChange(of: remindDaysRaw) { _, _ in ReminderScheduler.reschedule(context) }
            .onChange(of: remindTime) { _, _ in ReminderScheduler.reschedule(context) }
            .onChange(of: inactiveDays) { _, _ in ReminderScheduler.reschedule(context) }
            .alert(tr("Notifications are off", "Meldingen staan uit"), isPresented: $notificationsDenied) {
                Button(tr("Open Settings", "Open Instellingen")) {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                }
                Button(tr("Cancel", "Annuleren"), role: .cancel) {}
            } message: {
                Text(tr("Allow notifications for Liftr in Settings to get reminders.",
                        "Sta meldingen voor Liftr toe in Instellingen om herinneringen te krijgen."))
            }
            .refreshable { await cloud.refresh() }
            .sheet(isPresented: $showBody) { BodyMeasurementView() }
        }
    }
}

extension SettingsView {
    private static let weekdayOrder = [2, 3, 4, 5, 6, 7, 1] // Monday first

    private var selectedDays: Set<Int> {
        Set(remindDaysRaw.split(separator: ",").compactMap { Int($0) })
    }

    private func toggleDay(_ day: Int) {
        var days = selectedDays
        if days.contains(day) { days.remove(day) } else { days.insert(day) }
        remindDaysRaw = days.sorted().map(String.init).joined(separator: ",")
    }

    private var timeBinding: Binding<Date> {
        Binding(get: {
            Calendar.current.date(bySettingHour: remindTime / 60, minute: remindTime % 60, second: 0, of: Date()) ?? Date()
        }, set: { date in
            let c = Calendar.current.dateComponents([.hour, .minute], from: date)
            remindTime = (c.hour ?? 18) * 60 + (c.minute ?? 30)
        })
    }

    private func reminderChanged(turnedOn: Bool) {
        if turnedOn {
            Task {
                let granted = await ReminderScheduler.requestPermission()
                if !granted { notificationsDenied = true }
                ReminderScheduler.reschedule(context)
            }
        } else {
            ReminderScheduler.reschedule(context)
        }
    }

    @ViewBuilder
    var remindersSection: some View {
        Section {
            Toggle(tr("Training days", "Vaste trainingsdagen"), isOn: $remindDays)
            if remindDays {
                let labels = L10n.weekdaySymbols(.short)
                HStack(spacing: 6) {
                    ForEach(Array(Self.weekdayOrder.enumerated()), id: \.offset) { index, day in
                        let on = selectedDays.contains(day)
                        Button {
                            toggleDay(day)
                        } label: {
                            Text(labels[index])
                                .font(.subheadline.weight(.bold))
                                .frame(maxWidth: .infinity, minHeight: 36)
                                .background(on ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Theme.cardRaised),
                                            in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                                .foregroundStyle(on ? .white : Theme.muted)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            if remindDays || remindInactive {
                DatePicker(tr("Time", "Tijdstip"), selection: timeBinding, displayedComponents: .hourAndMinute)
            }
            Toggle(tr("When I haven't trained for a while", "Als ik een tijd niet heb getraind"), isOn: $remindInactive)
            if remindInactive {
                Stepper(value: $inactiveDays, in: 2...14) {
                    HStack {
                        Text(tr("After", "Na"))
                        Spacer()
                        Text(trf("{1} days", "{1} dagen", inactiveDays))
                            .foregroundStyle(Theme.accent)
                    }
                }
            }
            Toggle(tr("Weekly summary (Sunday 20:00)", "Weekoverzicht (zondag 20:00)"), isOn: $remindWeekly)
        } header: {
            Text(tr("Reminders", "Herinneringen"))
        } footer: {
            Text(tr("Reminders are planned on this device. A day you already trained (also on Apple Watch or iPad) is skipped.",
                    "Herinneringen worden op dit apparaat ingepland. Een dag waarop je al hebt getraind (ook op Apple Watch of iPad) wordt overgeslagen."))
        }
        .listRowBackground(Theme.card)
    }
}

/// Log weight (and optionally body fat); saved in Liftr and in Apple Health.
struct BodyMeasurementView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @AppStorage(WeightUnit.storageKey) private var unitRaw = "kg"
    @State private var date = Date()
    @State private var weightText = ""
    @State private var fatText = ""
    @State private var saving = false

    private var unit: WeightUnit { WeightUnit(rawValue: unitRaw) ?? .kg }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    DatePicker(tr("Date", "Datum"), selection: $date, in: ...Date())
                    HStack {
                        Text(tr("Weight", "Gewicht"))
                        Spacer()
                        TextField("0", text: $weightText)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 100)
                        Text(unit.symbol).foregroundStyle(Theme.muted)
                    }
                    HStack {
                        Text(tr("Body fat (optional)", "Vetpercentage (optioneel)"))
                        Spacer()
                        TextField("0", text: $fatText)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 80)
                        Text("%").foregroundStyle(Theme.muted)
                    }
                }
                .listRowBackground(Theme.card)
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(tr("Log weight", "Gewicht invoeren"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(tr("Cancel", "Annuleren")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(tr("Save", "Bewaar")) { save() }
                        .fontWeight(.bold)
                        .disabled((LiftrFormat.parse(weightText) ?? 0) <= 0 || saving)
                }
            }
        }
        .preferredColorScheme(.dark)
        .presentationDetents([.medium])
    }

    private func save() {
        guard let value = LiftrFormat.parse(weightText), value > 0 else { return }
        saving = true
        let kg = unit.toKg(value)
        let fat = LiftrFormat.parse(fatText)
        context.insert(BodyMeasurement(date: date, weightKg: kg, bodyFatPercent: fat ?? 0))
        try? context.save()
        Task {
            await HealthKitManager.shared.saveBody(weightKg: kg, bodyFatPercent: fat, date: date)
            dismiss()
        }
    }
}
