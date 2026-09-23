extends Node

enum ValueType{
    REQUIREMENT_TO_BUY, 
    REQUIREMENT_TO_SEE, 
    PRICE_EXPRESSION, 
    INCREASE_AMOUNT, 
    MAX_PURCHASE_AMOUNT, 
    BASE_VALUE
}

var small_coin_base_value: int = 0
var medium_coin_base_value: int = 0
var large_coin_base_value: int = 0

var requirements_to_buy: Dictionary = {}
var requirements_to_see: Dictionary = {}
var price_expressions_dict: Dictionary[String, Dictionary] = {}
var increase_amounts: Dictionary = {}
var max_purchase_amounts: Dictionary = {}
var base_values: Dictionary = {}

func _ready() -> void :
    _read_csv_game_values_file()
    _read_csv_price_expressions_file()

func get_value(value_type: ValueType, key: String) -> Variant:
    var dict = match_type_to_dict(value_type)
    if not dict.has(key):
        push_error("Key not found: " + key + " for type " + str(value_type))
        return null
    return dict[key]

func get_value_by_full_key(full_key: String) -> Variant:
    var prefixes = {
        "requirement_to_buy_": ValueType.REQUIREMENT_TO_BUY, 
        "requirement_to_see_": ValueType.REQUIREMENT_TO_SEE, 
        "price_expression_": ValueType.PRICE_EXPRESSION, 
        "increase_amount_": ValueType.INCREASE_AMOUNT, 
        "max_purchase_amount_": ValueType.MAX_PURCHASE_AMOUNT
    }

    for prefix in prefixes:
        if full_key.begins_with(prefix):
            var clean_key = full_key.substr(prefix.length())
            return get_value(prefixes[prefix], clean_key)


    if full_key.ends_with("_base_value"):
        var coin_type = full_key.replace("_base_value", "")
        return get_value(ValueType.BASE_VALUE, coin_type)

    push_error("Invalid key format: " + full_key)
    return null

func match_type_to_dict(value_type: ValueType) -> Dictionary:
    match value_type:
        ValueType.REQUIREMENT_TO_BUY: return requirements_to_buy
        ValueType.REQUIREMENT_TO_SEE: return requirements_to_see
        ValueType.PRICE_EXPRESSION: return price_expressions_dict
        ValueType.INCREASE_AMOUNT: return increase_amounts
        ValueType.MAX_PURCHASE_AMOUNT: return max_purchase_amounts
        ValueType.BASE_VALUE: return base_values
        _: return {}

func _read_csv_price_expressions_file():
    var file = FileAccess.open("res://balancing/price-expressions.csv", FileAccess.READ)
    var is_header = true
    while !file.eof_reached():
        var csv = file.get_csv_line()
        if is_header:
            is_header = false
            continue
        if csv.size() < 2:
            continue
        var key = csv[0].strip_edges()
        price_expressions_dict[key] = {
            "base": csv[1].to_float(), 
            "multiplier": csv[2].to_float(), 
            "flat_offset": csv[3].to_float()
        }
    file.close()

func _read_csv_game_values_file():
    var file = FileAccess.open("res://balancing/game-values.csv", FileAccess.READ)
    var is_header = true
    while !file.eof_reached():
        var csv = file.get_csv_line()
        if is_header:
            is_header = false
            continue
        if csv.size() < 2:
            continue

        var key = csv[0].strip_edges()
        var value = csv[1].strip_edges()

        if key.begins_with("requirement_to_buy_"):
            var clean_key = key.substr("requirement_to_buy_".length())
            requirements_to_buy[clean_key] = value
        elif key.begins_with("requirement_to_see_"):
            var clean_key = key.substr("requirement_to_see_".length())
            requirements_to_see[clean_key] = value
        elif key.begins_with("increase_amount_"):
            var clean_key = key.substr("increase_amount_".length())
            increase_amounts[clean_key] = value.to_float()
        elif key.begins_with("max_purchase_amount_"):
            var clean_key = key.substr("max_purchase_amount_".length())
            max_purchase_amounts[clean_key] = value.to_int()
        else:
            match (key):
                "small_coin_base_value":
                    small_coin_base_value = value.to_int()
                    base_values["small_coin"] = value.to_int()
                "medium_coin_base_value":
                    medium_coin_base_value = value.to_int()
                    base_values["medium_coin"] = value.to_int()
                "large_coin_base_value":
                    large_coin_base_value = value.to_int()
                    base_values["large_coin"] = value.to_int()
                _: push_error(key + " could not be mapped to a value.")
    file.close()
