#!/usr/bin/env python3
"""Add reverse runestone alchemy using SPD's existing scroll-to-stone mapping."""
from pathlib import Path
import sys


STONE_TO_SCROLL = """
\tpublic static class StoneToScroll extends Recipe {

\t\tprivate static final HashMap<Class<? extends Runestone>, Class<? extends Scroll>> scrolls = new HashMap<>();
\t\tstatic {
\t\t\tfor (Class<? extends Scroll> scroll : ScrollToStone.stones.keySet()) {
\t\t\t\tClass<? extends Runestone> stone = ScrollToStone.stones.get(scroll);
\t\t\t\tif (scrolls.put(stone, scroll) != null) {
\t\t\t\t\tthrow new IllegalStateException("Ambiguous runestone: " + stone.getSimpleName());
\t\t\t\t}
\t\t\t}
\t\t}

\t\t@Override
\t\tpublic boolean testIngredients(ArrayList<Item> ingredients) {
\t\t\tif (ingredients.size() != 2) return false;

\t\t\tItem first = ingredients.get(0);
\t\t\tItem second = ingredients.get(1);
\t\t\treturn first instanceof Runestone
\t\t\t\t\t&& first.getClass() == second.getClass()
\t\t\t\t\t&& first.quantity() > 0 && second.quantity() > 0
\t\t\t\t\t&& scrolls.containsKey(first.getClass())
\t\t\t\t\t&& (com.spd.blast.BlastSettings.enchantmentStoneReverseAlchemy()
\t\t\t\t\t\t\t|| !(first instanceof StoneOfEnchantment));
\t\t}

\t\t@Override
\t\tpublic int cost(ArrayList<Item> ingredients) {
\t\t\treturn 6;
\t\t}

\t\t/** Concrete output for inventory checks, without identifying it. */
\t\tpublic Scroll outputFor(ArrayList<Item> ingredients) {
\t\t\tif (!testIngredients(ingredients)) return null;
\t\t\treturn Reflection.newInstance(scrolls.get(ingredients.get(0).getClass()));
\t\t}

\t\t@Override
\t\tpublic Item brew(ArrayList<Item> ingredients) {
\t\t\tScroll scroll = outputFor(ingredients);
\t\t\tif (scroll == null) return null;
\t\t\tscroll.identify();
\t\t\tfor (Item stone : ingredients) {
\t\t\t\tstone.quantity(stone.quantity() - 1);
\t\t\t}
\t\t\treturn scroll;
\t\t}

\t\t@Override
\t\tpublic Item sampleOutput(ArrayList<Item> ingredients) {
\t\t\tScroll scroll = outputFor(ingredients);
\t\t\tif (scroll == null) return null;
\t\t\treturn scroll.isKnown() ? scroll : new Scroll.PlaceHolder();
\t\t}
\t}
"""


EXOTIC_TO_SCROLL = """
\tpublic static class ExoticToScroll extends Recipe {
\t\t@Override
\t\tpublic boolean testIngredients(ArrayList<Item> ingredients) {
\t\t\treturn ingredients.size() == 1
\t\t\t\t\t&& ingredients.get(0) instanceof ExoticScroll
\t\t\t\t\t&& ingredients.get(0).quantity() > 0
\t\t\t\t\t&& ExoticScroll.exoToReg.containsKey(ingredients.get(0).getClass());
\t\t}
\t\t@Override
\t\tpublic int cost(ArrayList<Item> ingredients) {
\t\t\treturn 6;
\t\t}
\t\t@Override
\t\tpublic Item brew(ArrayList<Item> ingredients) {
\t\t\tif (!testIngredients(ingredients)) return null;
\t\t\tExoticScroll exotic = (ExoticScroll) ingredients.get(0);
\t\t\tScroll scroll = Reflection.newInstance(ExoticScroll.exoToReg.get(exotic.getClass()));
\t\t\texotic.quantity(exotic.quantity() - 1);
\t\t\tscroll.identify();
\t\t\treturn scroll;
\t\t}
\t\t@Override
\t\tpublic Item sampleOutput(ArrayList<Item> ingredients) {
\t\t\tif (!testIngredients(ingredients)) return null;
\t\t\tExoticScroll exotic = (ExoticScroll) ingredients.get(0);
\t\t\treturn exotic.isKnown()
\t\t\t\t\t? Reflection.newInstance(ExoticScroll.exoToReg.get(exotic.getClass()))
\t\t\t\t\t: new Scroll.PlaceHolder();
\t\t}
\t}
"""

def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(old, new, 1)


def patch(scroll_path: Path, recipe_path: Path) -> None:
    scroll = scroll_path.read_text(encoding="utf-8")
    recipe = recipe_path.read_text(encoding="utf-8")

    if scroll.count("public static class ScrollToStone extends Recipe") != 1:
        raise SystemExit("Scroll.java: missing original ScrollToStone mapping")
    if "public static class StoneToScroll" in scroll:
        raise SystemExit("Scroll.java: reverse recipe already exists")
    if not scroll.endswith("\t}\n}\n"):
        raise SystemExit("Scroll.java: unexpected class ending")

    scroll = scroll[:-2] + "\n" + STONE_TO_SCROLL + EXOTIC_TO_SCROLL + "}\n"
    recipe = replace_once(
        recipe,
        "\tprivate static Recipe[] twoIngredientRecipes = new Recipe[]{\n",
        "\tprivate static Recipe[] twoIngredientRecipes = new Recipe[]{\n"
        "\t\tnew Scroll.StoneToScroll(),\n",
        "Recipe.java: two-ingredient recipe registration",
    )

    recipe = replace_once(
        recipe,
        "\tprivate static Recipe[] oneIngredientRecipes = new Recipe[]{\n",
        "\tprivate static Recipe[] oneIngredientRecipes = new Recipe[]{\n"
        "\t\tnew Scroll.ExoticToScroll(),\n",
        "Recipe.java: one-ingredient exotic reverse recipe",
    )

    scroll_path.write_text(scroll, encoding="utf-8")
    recipe_path.write_text(recipe, encoding="utf-8")
    print("patched Scroll.java and Recipe.java: reverse runestone alchemy")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: patch_stone_to_scroll.py <Scroll.java> <Recipe.java>")
    patch(Path(sys.argv[1]), Path(sys.argv[2]))
