extends Resource
class_name Purchasable

enum ATTRIBUTE_TO_UPGRADE_OR_UNLOCK{
    SMALL_COIN, 
    MEDIUM_COIN, 
    LARGE_COIN, 
    REFLIP_CHANCE, 
    SMALL_ADDITIONAL_COIN_VALUE, 
    FLIP_SMALL_COIN_ON_HOVER, 
    DICE, 
    LARGE_COINS_TRIGGER_COINS, 
    HELPER_TRIGGER_MEDIUM_COINS, 
    HELPER_TRIGGER_LARGE_COINS, 
    HELPER_TRIGGER_DIE, 
    HELPER, 
    SMALL_ADDITIONAL_COIN_VALUE_MULTIPLIER, 
    SMALL_COIN_FLIP_SPEED_MULTIPLIER, 
    HELPER_EFFICIENCY_MULTIPLIER, 
    MEDIUM_ADDITIONAL_COIN_VALUE, 
    LARGE_ADDITIONAL_COIN_VALUE, 
    MEDIUM_ADDITIONAL_COIN_VALUE_MULTIPLIER, 
    LARGE_ADDITIONAL_COIN_VALUE_MULTIPLIER, 
    MEDIUM_COIN_FLIP_SPEED_MULTIPLIER, 
    LARGE_COIN_FLIP_SPEED_MULTIPLIER
}

@export var attribute_to_upgrade_or_unlock: ATTRIBUTE_TO_UPGRADE_OR_UNLOCK
@export var icon: Texture2D

@export var name_key: String
@export var description_key: String

@export var requirement_to_see_expression_key: String = "NO_KEY"

@export var price_expression_key: String = "NO_KEY"
@export var increase_amount_key: = "NO_KEY"
@export var max_purchase_amount_key: = "NO_KEY"
@export var requirement_to_buy_expression_key: = "NO_KEY"
@export var increase_is_percentage: = false

func _on_buy():
    if StatsContext.purchases.has(name_key):
        StatsContext.purchases[name_key] += 1
    else:
        StatsContext.purchases[name_key] = 1

    match (attribute_to_upgrade_or_unlock):
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.SMALL_COIN:
            StatsContext.small_coins += GameValuesContext.get_value_by_full_key(increase_amount_key)
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.MEDIUM_COIN:
            StatsContext.medium_coins += GameValuesContext.get_value_by_full_key(increase_amount_key)
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.LARGE_COIN:
            StatsContext.large_coins += GameValuesContext.get_value_by_full_key(increase_amount_key)
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.REFLIP_CHANCE:
            StatsContext.coins_reflip_chance += GameValuesContext.get_value_by_full_key(increase_amount_key)
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.FLIP_SMALL_COIN_ON_HOVER:
            StatsContext.trigger_small_coins_on_mouse_over = true
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.DICE:
            StatsContext.die += GameValuesContext.get_value_by_full_key(increase_amount_key)
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.LARGE_COINS_TRIGGER_COINS:
            StatsContext.large_coins_trigger_coins_on_land = true
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.HELPER_TRIGGER_MEDIUM_COINS:
            StatsContext.helper_can_trigger_medium_coins = true
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.HELPER_TRIGGER_LARGE_COINS:
            StatsContext.helper_can_trigger_large_coins = true
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.HELPER_TRIGGER_DIE:
            StatsContext.helper_can_trigger_die = true
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.HELPER:
            StatsContext.helpers += GameValuesContext.get_value_by_full_key(increase_amount_key)
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.SMALL_ADDITIONAL_COIN_VALUE:
            StatsContext.small_coin_additional_coin_value += GameValuesContext.get_value_by_full_key(increase_amount_key)
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.MEDIUM_ADDITIONAL_COIN_VALUE:
            StatsContext.medium_coin_additional_coin_value += GameValuesContext.get_value_by_full_key(increase_amount_key)
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.LARGE_ADDITIONAL_COIN_VALUE:
            StatsContext.large_coin_additional_coin_value += GameValuesContext.get_value_by_full_key(increase_amount_key)
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.SMALL_COIN_FLIP_SPEED_MULTIPLIER:
            StatsContext.small_coin_flip_speed_multiplier += GameValuesContext.get_value_by_full_key(increase_amount_key)
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.MEDIUM_COIN_FLIP_SPEED_MULTIPLIER:
            StatsContext.medium_coin_flip_speed_multiplier += GameValuesContext.get_value_by_full_key(increase_amount_key)
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.LARGE_COIN_FLIP_SPEED_MULTIPLIER:
            StatsContext.large_coin_flip_speed_multiplier += GameValuesContext.get_value_by_full_key(increase_amount_key)
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.SMALL_ADDITIONAL_COIN_VALUE_MULTIPLIER:
            StatsContext.small_additional_coin_multiplier += GameValuesContext.get_value_by_full_key(increase_amount_key)
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.MEDIUM_ADDITIONAL_COIN_VALUE_MULTIPLIER:
            StatsContext.medium_additional_coin_multiplier += GameValuesContext.get_value_by_full_key(increase_amount_key)
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.LARGE_ADDITIONAL_COIN_VALUE_MULTIPLIER:
            StatsContext.large_additional_coin_multiplier += GameValuesContext.get_value_by_full_key(increase_amount_key)
        ATTRIBUTE_TO_UPGRADE_OR_UNLOCK.HELPER_EFFICIENCY_MULTIPLIER:
            StatsContext.helper_efficiency_multiplier += GameValuesContext.get_value_by_full_key(increase_amount_key)

func get_purchase_amount() -> int:
    if StatsContext.purchases.has(name_key):
        return StatsContext.purchases[name_key]
    else:
        return 0

func check_show_requirement() -> bool:
    var requirement_to_see_expression = GameValuesContext.get_value_by_full_key(requirement_to_see_expression_key)
    if requirement_to_see_expression.is_empty(): return true
    var expression = Expression.new()
    var error = expression.parse(requirement_to_see_expression, ["purchase_amount"])
    if error != OK:
        push_error(expression.get_error_text())
        return false
    var result = expression.execute([get_purchase_amount()], StatsContext)
    if not expression.has_execute_failed():
        return bool(result)
    return false

func check_buy_requirement() -> bool:
    var requirement_to_buy_expression = GameValuesContext.get_value_by_full_key(requirement_to_buy_expression_key)
    if requirement_to_buy_expression.is_empty(): return true
    var expression = Expression.new()
    var error = expression.parse(requirement_to_buy_expression, ["purchase_amount"])
    if error != OK:
        push_error(expression.get_error_text())
        return false
    var result = expression.execute([get_purchase_amount()], StatsContext)
    if not expression.has_execute_failed():
        return bool(result)
    return false

func check_buy_again_requirments() -> bool:
    if get_purchase_amount() >= GameValuesContext.get_value_by_full_key(max_purchase_amount_key): return false
    return true

func get_price() -> Big:
    var price_expression = GameValuesContext.get_value_by_full_key(price_expression_key)
    var base = Big.new(price_expression.base)
    var multiplier = Big.new(price_expression.multiplier)
    var flat_offset = Big.new(price_expression.flat_offset)
    var current_purchase_amount = get_purchase_amount()
    return Big.roundDown(Big.power(base, current_purchase_amount).times(multiplier).plus(flat_offset))
