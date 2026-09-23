extends TabContainer
class_name ShopSidebar

@onready var shop_item_scene: = preload("res://ui/shop_sidebar/shop_item/shop_item.tscn")

@onready var item_purchasables_list: = $SHOP_TAB_ITEMS / ScrollContainer / MarginContainer / VBoxContainer
@onready var upgrade_purchasables_list: = $SHOP_TAB_UPGRADES / ScrollContainer / MarginContainer / VBoxContainer

@export var item_purchasables: Array[Purchasable]
@export var upgrade_purchasables: Array[Purchasable]

var update_timer: = Timer.new()

signal sold_something

func _ready():
    for purchasable in item_purchasables:
        create_shop_item_from_purchasable_resource(purchasable, item_purchasables_list)
    for purchasable in upgrade_purchasables:
        create_shop_item_from_purchasable_resource(purchasable, upgrade_purchasables_list)

    update_timer.one_shot = false
    update_timer.wait_time = 1
    update_timer.timeout.connect(update_all_items)
    add_child(update_timer)
    update_timer.start()

    _load_from_save()
    update_all_items()

func _load_from_save():
    if not SaveContext.save_data.purchases: return
    if SaveContext.shop_has_been_loaded: return
    SaveContext.shop_has_been_loaded = true
    for purchasable in item_purchasables:
        if purchasable.name_key in SaveContext.save_data.purchases:
            for amount in range(SaveContext.save_data.purchases[purchasable.name_key]):
                purchasable._on_buy()

    for purchasable in upgrade_purchasables:
        if purchasable.name_key in SaveContext.save_data.purchases:
            for amount in range(SaveContext.save_data.purchases[purchasable.name_key]):
                purchasable._on_buy()
    update_all_items()
    sold_something.emit()


func create_shop_item_from_purchasable_resource(purchasable: Purchasable, parent):
    var item: ShopItem = shop_item_scene.instantiate()
    parent.add_child(item)
    item.bought.connect(func():
        update_all_items()
        SaveContext.save()
        AudioManager.play_sound_effect("buy_sound")
        sold_something.emit()
    )
    item.failed_buy.connect(func():
        AudioManager.play_sound_effect("cant_buy_sound")
    )
    item.purchasable = purchasable

func update_all_items():
    _sort_tab(item_purchasables_list)
    _sort_tab(upgrade_purchasables_list)

func _sort_tab(purchasable_list: Control):
    var sort_index = 0
    for child in purchasable_list.get_children():
        if child is ShopItem:
            AutomationContext.trigger_auto_buy(child)

            (child as ShopItem).update()
            (child as ShopItem).visible = (child as ShopItem).purchasable.check_show_requirement()
            if not child.visible: continue
            if (child as ShopItem).purchasable.get_purchase_amount() >= GameValuesContext.get_value_by_full_key((child as ShopItem).purchasable.max_purchase_amount_key): continue
            if not (child as ShopItem).purchasable.check_buy_requirement():
                purchasable_list.move_child(child, -1)
            else:

                purchasable_list.move_child(child, sort_index)
                sort_index += 1

func _on_tab_changed(_tab):
    update_all_items()
