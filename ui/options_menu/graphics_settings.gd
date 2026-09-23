extends ScrollContainer

func _ready() -> void :
    if SaveContext.save_data.options.has("fullscreen"):
        % FullscreenCheckButton.button_pressed = SaveContext.save_data.options["fullscreen"]

func _on_fullscreen_check_button_toggled(toggled_on: bool) -> void :
    SaveContext.save_data.options["fullscreen"] = toggled_on
    DisplayServer.window_set_mode(DisplayServer.WINDOW_MODE_EXCLUSIVE_FULLSCREEN if toggled_on else DisplayServer.WINDOW_MODE_MAXIMIZED)
