extends Node

const SAVE_PATH = "user://save.tres"
var save_data: SaveObject
var prevent_saving = false
var shop_has_been_loaded = false

func _ready():
    var args = OS.get_cmdline_args()
    for arg in args:
        if arg.begins_with("--automation"):
            prevent_saving = true
    if FileAccess.file_exists(SAVE_PATH) and not prevent_saving:
        save_data = ResourceLoader.load(SAVE_PATH) as SaveObject
        if not save_data:
            save_data = SaveObject.new()
    else:
        save_data = SaveObject.new()

func save():
    if prevent_saving: return
    print("Saved the game")
    save_data.purchases = StatsContext.purchases
    save_data.money = StatsContext.money
    save_data.highest_money = StatsContext.highest_money
    save_data.highest_value_gained = StatsContext.highest_value_gained
    save_data.total_money = StatsContext.total_money
    save_data.prestige_points = StatsContext.prestige_points
    ResourceSaver.save(save_data, SAVE_PATH)


func _on_timer_timeout() -> void :
    save()
