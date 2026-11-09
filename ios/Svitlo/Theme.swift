import SwiftUI

struct SvitloPalette {
    var paper: Color
    var ink: Color
    var muted: Color
    var card: Color
    var card2: Color
    var line: Color
    var powerOn: Color
    var powerOff: Color
    var onPowerOn: Color
    var onPowerOff: Color
    var powerOnText: Color
    var powerOffText: Color
    var accent: Color
    var onAccent: Color
    var amber: Color
    var note: Color
    var danger: Color
    var field: Color
    var now: Color
    var dark: Bool

    static let light = SvitloPalette(
        paper: Color(hex: 0xF4F0E6), ink: Color(hex: 0x1E1A14), muted: Color(hex: 0x6E655A),
        card: Color(hex: 0xFFFCF7), card2: Color(hex: 0xEBE4D6), line: Color(hex: 0xDDD4C4),
        powerOn: Color(hex: 0x7DDE96), powerOff: Color(hex: 0xD1543E),
        onPowerOn: Color(hex: 0x0E3B22), onPowerOff: Color(hex: 0x3C120C),
        powerOnText: Color(hex: 0x1E8F4A), powerOffText: Color(hex: 0xA33E2C),
        accent: Color(hex: 0x1C6E8A), onAccent: .white, amber: Color(hex: 0x8C6208),
        note: Color(hex: 0xF8EFCF), danger: Color(hex: 0xF8E3DC), field: Color(hex: 0xFFFCF7),
        now: Color(hex: 0x1E1A14), dark: false
    )

    static let dark = SvitloPalette(
        paper: Color(hex: 0x141311), ink: Color(hex: 0xF6F1E7), muted: Color(hex: 0xB6AB9C),
        card: Color(hex: 0x22201C), card2: Color(hex: 0x2E2A24), line: Color(hex: 0x3C362E),
        powerOn: Color(hex: 0x8EE4A8), powerOff: Color(hex: 0xE87864),
        onPowerOn: Color(hex: 0x0C3320), onPowerOff: Color(hex: 0x2C100C),
        powerOnText: Color(hex: 0x8EE4A8), powerOffText: Color(hex: 0xF0A090),
        accent: Color(hex: 0x8FD0E4), onAccent: Color(hex: 0x042028), amber: Color(hex: 0xE7C36A),
        note: Color(hex: 0x2C2718), danger: Color(hex: 0x2E1C18), field: Color(hex: 0x1A1916),
        now: Color(hex: 0xF6F1E7), dark: true
    )

    static func resolve(_ mode: ThemeMode, systemDark: Bool) -> SvitloPalette {
        switch mode {
        case .system: return systemDark ? .dark : .light
        case .light: return .light
        case .dark: return .dark
        }
    }
}
