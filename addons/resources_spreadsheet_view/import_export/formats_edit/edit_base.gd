class_name ResourceTablesEditFormat
extends RefCounted

var editor_view: Control


func get_value(entry, key: String):
    pass


func set_value(entry, key: String, value, index: int):
    pass


func save_entries(all_entries: Array, indices: Array):
    pass


func create_resource(entry) -> Resource:
    return Resource.new()


func duplicate_rows(rows: Array, name_input: String):
    pass


func delete_rows(rows: Array):
    pass


func has_row_names():
    return false


func import_from_path(folderpath: String, insert_func: Callable, sort_by: String, sort_reverse: bool = false) -> Array:
    return []
