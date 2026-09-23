extends RefCounted


const boxed_map: Dictionary = {
"a": "\n▒▄▀█\n░█▀█\n"\
\
\
, 
"b": "\n░█▄▄\n▒█▄█\n"\
\
\
, 
"c": "\n░▄▀▀\n░▀▄▄\n"\
\
\
, 
"d": "\n▒█▀▄\n░█▄▀\n"\
\
\
, 
"e": "\n░██▀\n▒█▄▄\n"\
\
\
, 
"f": "\n░█▀▀\n░█▀░\n"\
\
\
, 
"g": "\n▒▄▀▀\n░▀▄█\n"\
\
\
, 
"h": "\n░█▄█\n▒█▒█\n"\
\
\
, 
"i": "\n░█\n░█\n"\
\
\
, 
"j": "\n░░▒█\n░▀▄█\n"\
\
\
, 
"k": "\n░█▄▀\n░█▒█\n"\
\
\
, 
"l": "\n░█▒░\n▒█▄▄\n"\
\
\
, 
"m": "\n▒█▀▄▀█\n░█▒▀▒█\n"\
\
\
, 
"n": "\n░█▄░█\n░█▒▀█\n"\
\
\
, 
"o": "\n░█▀█\n▒█▄█\n"\
\
\
, 
"p": "\n▒█▀█\n░█▀▀\n"\
\
\
, 
"q": "\n░▄▀▄\n░▀▄█\n"\
\
\
, 
"r": "\n▒█▀█\n░█▀▄\n"\
\
\
, 
"s": "\n░▄▀\n▒▄█\n"\
\
\
, 
"t": "\n░▀█▀\n░▒█▒\n"\
\
\
, 
"u": "\n░█░█\n▒█▄█\n"\
\
\
, 
"v": "\n░█░█\n▒▀▄▀\n"\
\
\
, 
"w": "\n▒█░█░█\n░▀▄▀▄▀\n"\
\
\
, 
"x": "\n░▀▄▀\n░█▒█\n"\
\
\
, 
"y": "\n░▀▄▀\n░▒█▒\n"\
\
\
, 
"z": "\n░▀█\n▒█▄\n"\
\
\
, 
" ": "\n░\n░\n"\
\
\
, 
"_": "\n░░░\n▒▄▄\n"\
\
\
, 
",": "\n░▒\n░█\n"\
\
\
, 
".": "\n░░\n░▄\n"\
\
\
, 
"!": "\n░█\n░▄\n"\
\
\
, 
"-": "\n░▒░\n░▀▀\n"\
\
\
, 
"?": "\n░▀▀▄\n░▒█▀\n"\
\
\
, 
"'": "\n░▀\n░░\n"\
\
\
, 
":": "\n░▄░\n▒▄▒\n"\
\
\
, 
"0": "\n░▄▀▄\n░▀▄▀\n"\
\
\
, 
"1": "\n░▄█\n░░█\n"\
\
\
, 
"2": "\n░▀█\n░█▄\n"\
\
\
, 
"3": "\n░▀██\n░▄▄█\n"\
\
\
, 
"4": "\n░█▄\n░░█\n"\
\
\
, 
"5": "\n░█▀\n░▄█\n"\
\
\
, 
"6": "\n░█▀\n░██\n"\
\
\
, 
"7": "\n░▀█\n░█░\n"\
\
\
, 
"8": "\n░█▄█\n░█▄█\n"\
\
\
, 
"9": "\n░██\n░▄█\n"\
\
\
, 
}

const unsupported_char: = "\n░▒░\n▒░▒\n"





static func str_to_boxed_art(p_text: String) -> PackedStringArray:
    var lines: PackedStringArray = []
    lines.resize(2)
    for c in p_text:
        var ascii: String = boxed_map.get(c.to_lower(), unsupported_char)
        var parts: PackedStringArray = ascii.split("\n")
        lines[0] += parts[1]
        lines[1] += parts[2]
    return lines


static func is_boxed_art_supported(p_text: String) -> bool:
    for c in p_text:
        if not boxed_map.has(c.to_lower()):
            return false
    return true
