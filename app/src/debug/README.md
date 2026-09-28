# Debug launcher icon

The debug source set overrides the adaptive launcher icons with a grayscale variant. Release continues using the original artwork in `src/main`. The foreground remains transparent, matching the original adaptive-icon structure.

Artwork: `res/drawable-nodpi/melete_debug_icon.webp`, 432 × 432 — the adaptive-icon size at xxxhdpi (108 dp × 4), matching the release background. The full-size original is kept in `icon-source/`, outside the resources, so it is not packaged.

Created with the built-in image-generation/editing tool from `../main/res/mipmap-xxxhdpi/ic_launcher_bg.webp`. This is a generated grayscale variant, not a pixel-exact desaturation.

Prompt:

> Convert this existing app icon to black and white (grayscale). Preserve the illustration, face, expression, pose, composition, crop, and all details. Change only the colours to neutral shades of gray; no redesign, added text, borders, or badges. Keep the square full-bleed opaque background.

The debug build uses application ID `com.yokodake.melete.debug`, label **Melete Debug**, and version suffix `-debug`. Release retains `com.yokodake.melete`, its colour icon, and its existing signing key. Their private databases, settings and recovery copies are separate. See [the build guide](../../../docs/debug-release.md) for installation and testing.
