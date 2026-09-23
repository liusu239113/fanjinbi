extends PanelContainer
class_name ShopItem

@onready var label_name: = $MarginContainer / HSplitContainer / MarginContainer / HSplitContainer / CenterContainer / VSplitContainer / LabelName
@onready var label_price: = $MarginContainer / HSplitContainer / MarginContainer / HSplitContainer / CenterContainer2 / RichTextLabel
@onready var label_amount: = $MarginContainer / HSplitContainer / MarginContainer / HSplitContainer / CenterContainer / VSplitContainer / LabelAmount
@onready var texture: = $MarginContainer / HSplitContainer / TextureRect

signal bought
signal failed_buy

@export var purchasable: Purchasable:
    set(value):
        purchasable = value
        update()

func update():
    texture.texture = purchasable.icon
    var label_amount_text = ""
    var has_all: bool = purchasable.get_purchase_amount() >= GameValuesContext.get_value_by_full_key(purchasable.max_purchase_amount_key)
    if not purchasable.check_show_requirement():
        label_price.text = "?" + tr("CURRENCY")
        label_name.text = "???"
        texture.modulate = Color.BLACK
        label_amount_text = "?" + "/" + "?"
    else:
        label_price.text = str(BigHelper.convert_big_to_text(Big.new(purchasable.get_price())) + tr("CURRENCY"))
        if has_all:
            label_price.text = "-"
        label_name.text = tr(purchasable.name_key)
        texture.modulate = Color.WHITE

        if StatsContext.money.isLessThan(purchasable.get_price()) and not has_all:
            label_price.modulate = Color.RED
        else:
            label_price.modulate = Color.WHITE

        if not purchasable.check_buy_again_requirments():
            modulate.a = 0.5
        label_amount_text = str(purchasable.get_purchase_amount()) + "/" + str(GameValuesContext.get_value_by_full_key(purchasable.max_purchase_amount_key))
    label_amount.text = BBCodeHelper.build(label_amount_text).color(Color.DARK_GRAY.to_html()).result()


func _on_mouse_entered_or_focus_entered():
    if purchasable.check_show_requirement():
        var increase_amount
        if purchasable.increase_is_percentage:
            increase_amount = int(GameValuesContext.get_value_by_full_key(purchasable.increase_amount_key) * 100)
        else:
            increase_amount = int(GameValuesContext.get_value_by_full_key(purchasable.increase_amount_key))
        match (purchasable.name_key):
            "LARGE_COIN_NAME": increase_amount = GameValuesContext.get_value_by_full_key("large_coin_base_value")
            "MEDIUM_COIN_NAME": increase_amount = GameValuesContext.get_value_by_full_key("medium_coin_base_value")
            "SMALL_COIN_NAME": increase_amount = GameValuesContext.get_value_by_full_key("small_coin_base_value")


        TooltipOverlay.describe(self, tr(purchasable.description_key).replace("{increase_amount}", str(increase_amount)))
    else:
        TooltipOverlay.describe(self, "???")

func _on_button_pressed():
    on_buy()

func can_buy():
    return purchasable.check_buy_again_requirments() and purchasable.check_show_requirement() and StatsContext.money.isGreaterThanOrEqualTo(Big.roundDown(purchasable.get_price()))

func on_buy():
    if can_buy():
        StatsContext.money = Big.subtract(StatsContext.money, Big.new(purchasable.get_price()))
        purchasable._on_buy()
        bought.emit()
    else:
        failed_buy.emit()
