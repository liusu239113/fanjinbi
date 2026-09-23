class_name BBCodeHelper

var content = ""
var empty_image: Texture = preload("res://godot_tooltip_component_by_greenpixels/lib/util/bb_code_helper/empty.png")

static func build(_content: String) -> BBCodeHelper:
    var instance = BBCodeHelper.new()
    instance.content = _content
    return instance

func color(hex: String) -> BBCodeHelper:
    content = "[color={hex}]{content}[/color]".format({"hex": hex, "content": content})
    return self

func add_icon(texture: Texture, size: int = 21, before = true) -> BBCodeHelper:
    var width = (texture.get_width() / texture.get_height()) * size
    var empty_image_string = "[img=4x{size_y}]{empty_path}[/img]".format({"empty_path": empty_image.resource_path, "size_y": size})
    var image_string = "[img={size_x}x{size_y}]{path}[/img]".format({
        "path": texture.resource_path, 
        "size_x": width, 
        "size_y": size
        })
    content = image_string + empty_image_string + content if before else content + empty_image_string + image_string
    return self

func replace_key_as_coloured_number_value(key: String, value: float, font_color: String, add_sign = false) -> BBCodeHelper:
    var _sign = ""
    if add_sign: _sign = "+" if value > 0 else ""
    content = content.format({key: BBCodeHelper.build(_sign + str(value)).color(font_color).result()})
    return self

func center() -> BBCodeHelper:
    content = "[center]{content}[/center]".format({"content": content})
    return self

func result():
    return content
