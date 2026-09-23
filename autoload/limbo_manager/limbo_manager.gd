extends Node

var should_hide_tooltips: = false

func _ready() -> void :
    if OS.has_feature("standalone"):
        LimboConsole.enabled = false
    LimboConsole.register_command(_set_prestige_points, "prestige", "Sets the amount of prestige points")
    LimboConsole.register_command(_set_money, "money", "Sets the currenty amount of money")
    LimboConsole.register_command(_set_table, "table", "Sets the sprite of the table")
    LimboConsole.register_command(_set_language, "language", "Set the current locale and restarts the scene")
    LimboConsole.register_command(_reset_save, "reset", "Wipes the save data and prevents further saving. Restart after the command to start a new game.")
    LimboConsole.register_command(_toggle_tooltips, "tooltip_toggle", "Hides or shows the tooltips")
    LimboConsole.register_command(_toggle_directional_flip, "directional_flip", "When activated, coins flip away from the cursor")
    LimboConsole.register_command(_set_item_radius, "item_radius", "Sets the radius for items. Default is 32px")

func _set_money(value: String):
    StatsContext.money = Big.new(value)

func _set_language(locale: String):
    TranslationServer.set_locale(locale)

func _set_table(table_key: String):
    var play_room = get_tree().current_scene
    if play_room is PlayRoom:
        play_room.table_sprite_key = table_key

func _toggle_tooltips():
    should_hide_tooltips = !should_hide_tooltips
    if should_hide_tooltips:
        LimboConsole.info("Tooltips are now hidden")
    else:
        LimboConsole.info("Tooltips are now visible")

func _reset_save():
    var status = DirAccess.open("user://").remove(SaveContext.SAVE_PATH)
    if status == OK:
        SaveContext.prevent_saving = true
        LimboConsole.info("Deleted save-file. Please restart the game.")
    else:
        LimboConsole.error("Unable to delete save file")

func _set_prestige_points(value: String):
    StatsContext.prestige_points = Big.new(value)

func _toggle_directional_flip():
    OptionContext.__dev_enabled_directional_move = !OptionContext.__dev_enabled_directional_move
    if OptionContext.__dev_enabled_directional_move:
        LimboConsole.info("Directional Flip enabled")
    else:
        LimboConsole.info("Diretional Move disabled")



func _set_item_radius(radius_input: String):
    if not radius_input.is_valid_int():
        LimboConsole.error("Not a valid number")
    var radius = radius_input.to_int()
    Item.collision_distance_diameter_squared = pow(radius, 2) * 2
    LimboConsole.info("Radius has been set to " + str(radius) + "px")
