extends Node

@export var mouse_cursor_normal: Texture2D
@export var mouse_cusor_pressed: Texture2D
var should_hide_money_labels: = false
var __dev_enabled_directional_move: = false
var is_mouse_pressed: = false:
    set(value):
        is_mouse_pressed = value
        Input.set_custom_mouse_cursor(mouse_cusor_pressed if is_mouse_pressed else mouse_cursor_normal)

func _ready() -> void :
    is_mouse_pressed = false

func _input(event: InputEvent) -> void :
    if event is InputEventMouseButton:
        is_mouse_pressed = (event as InputEventMouseButton).pressed
