extends PanelContainer

var orignal_scale: Vector2
var url = "https://store.steampowered.com/app/3618390?utm_source=UNKNOWN_OR_DESKTOP&utm_medium=PLATFORM_prototype&utm_content=ingame_button"

func _ready() -> void :
    if OS.has_feature("web"):
        var host = JavaScriptBridge.eval("location.hostname", true)
        url = url.replace("UNKNOWN_OR_DESKTOP", host)
    url = url.replace("PLATFORM", OS.get_name().to_lower())
    orignal_scale = scale
    var animation = TweenHelper.tween("animation", self)
    animation.set_loops()
    animation.tween_property(self, "modulate", Color.from_rgba8(150, 150, 150, 255), 0.2).set_delay(2).set_ease(Tween.EASE_OUT)
    animation.tween_property(self, "modulate", Color.WHITE, 0.2).set_ease(Tween.EASE_OUT)

func _on_button_pressed() -> void :
    OS.shell_open(url)
