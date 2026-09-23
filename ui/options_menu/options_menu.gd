extends CanvasLayer

func _ready() -> void :
    hide()


func _on_close_button_pressed() -> void :
    hide()
    SaveContext.save()
