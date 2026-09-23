extends Button

func _on_pressed() -> void :
    TransitionHandler.transition_to(load("res://rooms/play_room/play_room.tscn"), 1)
