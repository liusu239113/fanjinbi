class_name ResourceTablesCellEditor
extends RefCounted

const TextEditingUtilsClass: = preload("res://addons/resources_spreadsheet_view/text_editing_utils.gd")

const CELL_SCENE_DIR: = "res://addons/resources_spreadsheet_view/typed_cells/"

var hint_strings_array: = []



func can_edit_value(value, type, property_hint, column_index) -> bool:
    return value != null



func create_cell(caller: Control) -> Control:
    return load(CELL_SCENE_DIR + "basic.tscn").instantiate()


func set_selected(node: Control, selected: bool):
    node.get_node("Selected").visible = selected


func set_value(node: Control, value):
    node.text = TextEditingUtilsClass.show_non_typing(str(value))


func is_text():
    return true


func to_text(value) -> String:
    return var_to_str(value)


func from_text(text: String):
    return str_to_var(text)


func set_color(node: Control, color: Color):
    node.get_node("Back").modulate = color * 1.0
