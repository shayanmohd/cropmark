# Cropmark design notes

**Reading this as:** a precise document tool for anxious applicants and counter operators, with a
photo-booth measuring language, leaning toward Sofia Sans Condensed plus Public Sans on a booth grey
and registration magenta palette.

**Dials**

- DESIGN_VARIANCE 3. Nervous users need predictable layouts: one centred column capped at 600dp on
  phones, photo left and controls right at 600dp and wider. The only asymmetry is the millimetre
  ruler that hugs the right edge of the photo.
- MOTION_INTENSITY 2. Only the spec frame moves, and only because a face moved: brackets follow the
  tracked face in 120 ms, a background swap cross-fades in 150 ms. One first-run moment: the first
  tracked face pulls the brackets in from the edges over 350 ms. Reduced motion snaps all of it.
- VISUAL_DENSITY 4. Controls sit beside or under the photo, never over it. Lists are unboxed rows
  separated by spacing; cards are not used as a layout device.

**The one memorable thing:** the spec frame. Magenta crop-corner brackets for crown and chin, a
magenta eye band and a millimetre ruler on the right edge, drawn on the live camera and kept on the
editor and the checks screen, so the rule is always where the photo is.

## Tokens

| Token | Light | Dark | Role |
|---|---|---|---|
| Backdrop | #EEF0F3 | #15181D | background, surface, window |
| Panel | #DFE3E9 | #20252C | sheets, fields, control strip |
| Ink | #1A1F27 | #E6E9EE | text, primary button fill |
| Slate | #525C69 | #9BA5B2 | secondary text, ruler numerals, outlines |
| Rule | #B7BFCA | #3A424D | dividers, skeletons (never text) |
| Magenta | #B0276A | #E5639F | the one accent: frame, ruler range, selection, fix rows, focus |

Buttons are Ink with Backdrop labels. Over photos, guides carry a 1.5dp Backdrop halo. Output
backgrounds (white, light blue, light grey) are spec pixel values, not theme tokens.

## Type

- Sofia Sans Condensed SemiBold: titles 22sp, measurements ("35 x 45 mm") 28sp, ruler numerals 12sp,
  tabular figures on.
- Public Sans: body 16sp Regular, labels 14sp Medium, buttons 15sp SemiBold.

## Shape

0 for anything standing for paper (photo, sheet, size diagram). RadiusSm 6dp chips and swatches,
RadiusMd 12dp buttons and fields, RadiusLg 20dp bottom sheet tops.
