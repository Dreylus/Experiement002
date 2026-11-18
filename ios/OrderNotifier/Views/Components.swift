import SwiftUI
import UIKit

// MARK: - Liquid Glass (iOS 26) with frosted-material fallbacks

@available(iOS 26.0, *)
private func makeGlass(tint: Color?, interactive: Bool) -> Glass {
    var glass = Glass.regular
    if let tint { glass = glass.tint(tint) }
    if interactive { glass = glass.interactive() }
    return glass
}

extension View {
    /// A Liquid Glass panel on iOS 26, a frosted material panel before that.
    @ViewBuilder
    func glassSurface(cornerRadius: CGFloat = 26, tint: Color? = nil, interactive: Bool = false) -> some View {
        if #available(iOS 26.0, *) {
            self.glassEffect(
                makeGlass(tint: tint, interactive: interactive),
                in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            )
        } else {
            self
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .strokeBorder(.white.opacity(0.18), lineWidth: 0.5)
                }
        }
    }

    /// The main call to action: glass-prominent on iOS 26.
    @ViewBuilder
    func prominentGlassButton(tint: Color? = nil) -> some View {
        if #available(iOS 26.0, *) {
            self.buttonStyle(.glassProminent).tint(tint)
        } else {
            self.buttonStyle(.borderedProminent).tint(tint)
        }
    }

    /// Secondary actions: clear glass on iOS 26.
    @ViewBuilder
    func glassButton() -> some View {
        if #available(iOS 26.0, *) {
            self.buttonStyle(.glass)
        } else {
            self.buttonStyle(.bordered)
        }
    }
}

/// Groups glass shapes so they blend and morph together on iOS 26.
struct GlassGroup<Content: View>: View {
    var spacing: CGFloat? = nil
    @ViewBuilder var content: Content

    var body: some View {
        if #available(iOS 26.0, *) {
            GlassEffectContainer(spacing: spacing) { content }
        } else {
            content
        }
    }
}

// MARK: - Background

/// Soft color behind the glass so it has something to refract.
struct AppBackground: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        ZStack {
            Color(uiColor: .systemGroupedBackground)
            if #available(iOS 18.0, *) {
                MeshGradient(
                    width: 3,
                    height: 3,
                    points: [
                        [0, 0], [0.5, 0], [1, 0],
                        [0, 0.5], [0.6, 0.45], [1, 0.5],
                        [0, 1], [0.5, 1], [1, 1],
                    ],
                    colors: colors
                )
                .opacity(scheme == .dark ? 0.5 : 0.45)
            } else {
                LinearGradient(
                    colors: [colors[0], colors[4], colors[8]],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .opacity(scheme == .dark ? 0.45 : 0.35)
            }
        }
        .ignoresSafeArea()
    }

    private var colors: [Color] {
        if scheme == .dark {
            return [.teal, .indigo, .black, .green, .black, .purple, .black, .teal, .indigo]
        }
        return [.mint, .cyan, .blue.opacity(0.4), .green.opacity(0.5), .white, .teal.opacity(0.5), .white, .mint.opacity(0.6), .green.opacity(0.35)]
    }
}

// MARK: - App icon

/// The current app icon picture, drawn like a Home Screen icon.
struct BrandIconView: View {
    @Environment(AppModel.self) private var model
    var size: CGFloat

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: size * 0.2237, style: .continuous)
        Group {
            if UIImage(named: model.currentIconPreview) != nil {
                Image(model.currentIconPreview)
                    .resizable()
                    .scaledToFill()
            } else {
                ZStack {
                    Color.gray
                    Image(systemName: "bag.fill")
                        .font(.system(size: size * 0.42))
                        .foregroundStyle(.white)
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(shape)
        .overlay { shape.strokeBorder(.primary.opacity(0.08), lineWidth: 0.5) }
        .accessibilityHidden(true)
    }
}

// MARK: - Alert preview

/// Looks like the real iOS banner, filled with the current settings.
struct NotificationPreview: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        HStack(alignment: .top, spacing: 11) {
            BrandIconView(size: 38)
            VStack(alignment: .leading, spacing: 1) {
                HStack(alignment: .firstTextBaseline) {
                    Text(model.title(for: model.upcomingNumber))
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    Spacer(minLength: 8)
                    Text("now")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                if let subtitle = model.subtitle {
                    Text(subtitle)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                }
                Text(model.previewBody)
                    .font(.subheadline)
                    .lineLimit(4)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassSurface(cornerRadius: 24)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Preview of an order alert")
    }
}

// MARK: - Small pieces

struct StatTile: View {
    let value: String
    let label: String

    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.title3.weight(.bold))
                .fontDesign(.rounded)
                .contentTransition(.numericText())
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
    }
}

struct CardTitle: View {
    let title: String
    var symbol: String? = nil

    var body: some View {
        HStack(spacing: 6) {
            if let symbol { Image(systemName: symbol) }
            Text(title)
        }
        .font(.headline)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
