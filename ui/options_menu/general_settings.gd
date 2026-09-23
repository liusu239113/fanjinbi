extends ScrollContainer

enum LANGUAGE{
    ENGLISH, 
    GERMAN
}

func _ready() -> void :
    if OS.get_name() == "Web":
        % CloseGameButton.hide()
    if SaveContext.save_data.options.has("language"):
        % OptionButton.select(SaveContext.save_data.options["language"])
        _on_option_button_item_selected(SaveContext.save_data.options["language"])
    else:
        % OptionButton.select(0)
        _on_option_button_item_selected(0)

    if SaveContext.save_data.options.has("hide_money_labels"):
        % HideMoneyLabelCheckButton.button_pressed = SaveContext.save_data.options["hide_money_labels"]

func _on_option_button_item_selected(index: int) -> void :
    match (index):
        LANGUAGE.ENGLISH: TranslationServer.set_locale("en")
        LANGUAGE.GERMAN: TranslationServer.set_locale("de")
    SaveContext.save_data.options["language"] = index


func _on_close_game_button_pressed() -> void :
    SaveContext.save()
    await get_tree().process_frame
    get_tree().quit()












func _on_hide_money_label_check_button_toggled(toggled_on: bool) -> void :
    SaveContext.save_data.options["hide_money_labels"] = toggled_on
    OptionContext.should_hide_money_labels = toggled_on
