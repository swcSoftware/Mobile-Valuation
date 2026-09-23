import SwiftUI

/// Lets the reader choose the app's chrome color.
///
/// Two rules it enforces rather than trusts:
///  1. An accent that cannot reach 3:1 against the current background is shown as unavailable, with
///     the reason, instead of producing a button nobody can read.
///  2. Price and value are never offered here. They are the one pair the whole app depends on
///     telling apart (docs/DESIGN.md, "Semantic colors").
struct AccentPicker: View {
    @Binding var selection: UInt32?
    @Environment(AppSettings.self) private var settings

    private var ground: RGB {
        RGB(hex: Theme.current.colorScheme == .dark ? 0x0B0D10 : 0xF1F4F5)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Accent color")
                Spacer()
                if selection != nil {
                    Button("Reset") { selection = nil }
                        .font(.caption)
                        .buttonStyle(.plain)
                        .foregroundStyle(Theme.accent)
                }
            }

            LazyVGrid(columns: [GridItem(.adaptive(minimum: 44), spacing: 10)], alignment: .leading, spacing: 10) {
                ForEach(AccentChoice.presets, id: \.hex) { preset in
                    swatch(preset.name, preset.hex)
                }
            }
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private func swatch(_ name: String, _ hex: UInt32) -> some View {
        let legible = RGB(hex: hex).isLegible(on: ground)
        let chosen = selection == hex
        Button {
            selection = chosen ? nil : hex
        } label: {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(Color(hex: hex))
                .frame(height: 44)
                .overlay {
                    if chosen {
                        Image(systemName: "checkmark")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(RGB(hex: hex).readableForeground)
                    } else if !legible {
                        Image(systemName: "slash.circle")
                            .font(.subheadline)
                            .foregroundStyle(RGB(hex: hex).readableForeground.opacity(0.8))
                    }
                }
                .overlay {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(chosen ? Theme.textPrimary : Theme.border, lineWidth: chosen ? 2 : 1)
                }
                .opacity(legible ? 1 : 0.45)
        }
        .buttonStyle(.plain)
        .disabled(!legible)
        .accessibilityLabel(legible
                            ? "\(name) accent\(chosen ? ", selected" : "")"
                            : "\(name) accent, unavailable — too little contrast with this background")
    }
}
