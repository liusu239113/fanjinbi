extends Resource
class_name SaveObject

@export_storage var purchases: Dictionary[String, int] = {}
@export_storage var money: Big = Big.new(0)
@export_storage var highest_money: Big = Big.new()
@export_storage var highest_value_gained: Big = Big.new()
@export_storage var options: Dictionary[String, Variant] = {}
@export_storage var prestige_points: Big = Big.new()
@export_storage var total_money: Big = Big.new()
