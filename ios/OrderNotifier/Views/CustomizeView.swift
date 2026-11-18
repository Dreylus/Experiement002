import SwiftUI
import UIKit

struct CustomizeView: View {
    @Environment(AppModel.self) private var model
    @FocusState private var focus: Field?

    enum Field: Hashable {
        case store, from, title, currency, number
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    NotificationPreview()
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                } footer: {
                    Text("This is how each order appears as a banner and on your Lock Screen.")
                }

                Section("Alert text") {
                    textRow("Store name", key: \.storeName, max: 30, field: .store, prompt: "Store", id: "field.storeName")
                    textRow("From text", key: \.fromText, max: 40, field: .from, prompt: "Hidden", id: "field.fromText")
                    textRow("Order title", key: \.titlePrefix, max: 20, field: .title, prompt: "Order #", id: "field.titlePrefix")
                    textRow("Currency", key: \.currency, max: 4, field: .currency, prompt: "None", id: "field.currency")
                    LabeledContent("Next order number") {
                        TextField("1001", value: model.nextNumberBinding, format: .number.grouping(.never))
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                            .focused($focus, equals: .number)
                            .accessibilityIdentifier("field.nextOrder")
                    }
                }

                Section {
                    Toggle("Show “from \(model.settings.storeName)” line", isOn: model.binding(\.showSubtitle))
                    Toggle("Add store name after the from text", isOn: model.binding(\.storeSuffix))
                } footer: {
                    Text("The from text is what comes after “from” in each order. Leave it empty to hide that part.")
                }

                Section("Order amounts") {
                    PriceRange()
                    Stepper(value: model.binding(\.maxItems), in: 1...10) {
                        LabeledContent(
                            "Items per order",
                            value: model.settings.maxItems == 1 ? "1" : "1 – \(model.settings.maxItems)"
                        )
                    }
                }

                Section {
                    IconPicker()
                } header: {
                    Text("App icon and name")
                } footer: {
                    Text("iPhone shows the app's icon and name on every alert. To use your own picture or name, replace ios/Branding/AppIcon.png or edit ios/Branding/branding.json on GitHub, then install the new build. You can also rename the app in Sideloadly's Advanced Options when you install. Pictures you add to ios/Branding/Icons appear here to switch between.")
                }
            }
            .scrollContentBackground(.hidden)
            .background(AppBackground())
            .navigationTitle("Customize")
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Done") { focus = nil }
                        .accessibilityIdentifier("keyboard.done")
                }
            }
        }
    }

    private func textRow(
        _ label: String, key: WritableKeyPath<OrderSettings, String>, max: Int,
        field: Field, prompt: String, id: String
    ) -> some View {
        LabeledContent(label) {
            TextField(prompt, text: model.textBinding(key, max: max))
                .multilineTextAlignment(.trailing)
                .focused($focus, equals: field)
                .submitLabel(.done)
                .autocorrectionDisabled()
                .accessibilityIdentifier(id)
        }
    }
}

// MARK: - Price range

private struct PriceRange: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let c = model.settings.currency
        let lo = min(model.settings.minPrice, model.settings.maxPrice)
        let hi = max(model.settings.minPrice, model.settings.maxPrice)
        VStack(alignment: .leading, spacing: 6) {
            LabeledContent("Order total", value: lo == hi ? "\(c)\(lo)" : "\(c)\(lo) – \(c)\(hi)")
            StepSlider(title: "Lowest", steps: TimingMath.priceSteps, value: lo, format: { "\(c)\($0)" }) {
                model.setPrice(min: $0)
            }
            StepSlider(title: "Highest", steps: TimingMath.priceSteps, value: hi, format: { "\(c)\($0)" }) {
                model.setPrice(max: $0)
            }
        }
    }
}

/// A slider that snaps to a list of values (so small and large values are both easy to pick).
struct StepSlider: View {
    let title: String
    let steps: [Int]
    let value: Int
    let format: (Int) -> String
    let onChange: (Int) -> Void

    var body: some View {
        HStack(spacing: 10) {
            Text(title)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .frame(width: 62, alignment: .leading)
            Slider(
                value: Binding(
                    get: { Double(TimingMath.nearestIndex(steps, value)) },
                    set: { newValue in
                        let index = min(max(Int(newValue.rounded()), 0), steps.count - 1)
                        if steps[index] != value { onChange(steps[index]) }
                    }
                ),
                in: 0...Double(steps.count - 1),
                step: 1
            )
            .accessibilityLabel(title)
            .accessibilityValue(format(value))
            Text(format(value))
                .font(.subheadline.weight(.semibold).monospacedDigit())
                .frame(minWidth: 54, alignment: .trailing)
        }
    }
}

// MARK: - App icon

private struct IconPicker: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 18) {
                ForEach(model.icons) { option in
                    let selected = option.iconName == model.currentIconName
                    Button {
                        Task { await model.setIcon(option) }
                    } label: {
                        VStack(spacing: 6) {
                            iconImage(option.preview)
                                .frame(width: 60, height: 60)
                                .clipShape(RoundedRectangle(cornerRadius: 13.4, style: .continuous))
                                .overlay {
                                    // Hairline so dark icons stay visible on dark rows.
                                    RoundedRectangle(cornerRadius: 13.4, style: .continuous)
                                        .strokeBorder(Color.primary.opacity(0.15), lineWidth: 0.5)
                                }
                                .overlay {
                                    RoundedRectangle(cornerRadius: 17, style: .continuous)
                                        .strokeBorder(selected ? Color.accentColor : .clear, lineWidth: 2.5)
                                        .padding(-4)
                                }
                            Text(option.title)
                                .font(.caption)
                                .foregroundStyle(selected ? Color.primary : Color.secondary)
                                .lineLimit(1)
                        }
                        .padding(4)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(option.title) icon")
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
            }
            .padding(.vertical, 6)
        }
        LabeledContent("App name", value: model.appName)
    }

    @ViewBuilder
    private func iconImage(_ name: String) -> some View {
        if UIImage(named: name) != nil {
            Image(name).resizable().scaledToFill()
        } else {
            ZStack {
                Color.gray
                Image(systemName: "bag.fill").foregroundStyle(.white)
            }
        }
    }
}
