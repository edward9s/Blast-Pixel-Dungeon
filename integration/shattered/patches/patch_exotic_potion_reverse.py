#!/usr/bin/env python3
"""Add reverse exotic-potion alchemy to Shattered."""
from pathlib import Path
import sys


EXOTIC_TO_POTION = """
\tpublic static class ExoticToPotion extends Recipe {
\t\t@Override
\t\tpublic boolean testIngredients(ArrayList<Item> ingredients) {
\t\t\treturn ingredients.size() == 1
\t\t\t\t\t&& ingredients.get(0) instanceof ExoticPotion
\t\t\t\t\t&& ingredients.get(0).quantity() > 0
\t\t\t\t\t&& exoToReg.containsKey(ingredients.get(0).getClass());
\t\t}

\t\t@Override
\t\tpublic int cost(ArrayList<Item> ingredients) {
\t\t\treturn 4;
\t\t}

\t\t@Override
\t\tpublic Item brew(ArrayList<Item> ingredients) {
\t\t\tif (!testIngredients(ingredients)) return null;

\t\t\tExoticPotion exotic = (ExoticPotion) ingredients.get(0);
\t\t\tPotion potion = Reflection.newInstance(exoToReg.get(exotic.getClass()));
\t\t\texotic.quantity(exotic.quantity() - 1);
\t\t\tpotion.identify();
\t\t\treturn potion;
\t\t}

\t\t@Override
\t\tpublic Item sampleOutput(ArrayList<Item> ingredients) {
\t\t\tif (!testIngredients(ingredients)) return null;

\t\t\tExoticPotion exotic = (ExoticPotion) ingredients.get(0);
\t\t\treturn exotic.isKnown()
\t\t\t\t\t? Reflection.newInstance(exoToReg.get(exotic.getClass()))
\t\t\t\t\t: new Potion.PlaceHolder();
\t\t}
\t}
"""


def replace_once(source: str, anchor: str, replacement: str, label: str) -> str:
    count = source.count(anchor)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(anchor, replacement, 1)


def patch(potion_path: Path, recipe_path: Path, guide_path: Path) -> None:
    potion = potion_path.read_text(encoding="utf-8")
    recipe = recipe_path.read_text(encoding="utf-8")
    guide = guide_path.read_text(encoding="utf-8")

    if potion.count("public static class PotionToExotic extends Recipe") != 1:
        raise SystemExit("ExoticPotion.java: expected original PotionToExotic")
    if "public static class ExoticToPotion" in potion:
        raise SystemExit("ExoticPotion.java: reverse recipe already exists")
    if not potion.endswith("\t}\n}\n"):
        raise SystemExit("ExoticPotion.java: unexpected class ending")

    potion = potion[:-2] + "\n" + EXOTIC_TO_POTION + "}\n"
    recipe = replace_once(
        recipe,
        "\tprivate static Recipe[] oneIngredientRecipes = new Recipe[]{\n",
        "\tprivate static Recipe[] oneIngredientRecipes = new Recipe[]{\n"
        "\t\tnew ExoticPotion.ExoticToPotion(),\n",
        "Recipe.java: one-ingredient reverse potion registration",
    )
    guide = replace_once(
        guide,
        "\t\t\t\treturn result;\n\t\t\tcase 4:\n",
        "\t\t\t\tresult.add(null);\n"
        "\t\t\t\tr = new ExoticPotion.ExoticToPotion();\n"
        "\t\t\t\tfor (Class<? extends ExoticPotion> cls : ExoticPotion.exoToReg.keySet()) {\n"
        "\t\t\t\t\tExoticPotion exotic = Reflection.newInstance(cls);\n"
        "\t\t\t\t\tArrayList<Item> inputs = new ArrayList<>(Arrays.asList(exotic));\n"
        "\t\t\t\t\tresult.add(new QuickRecipe(r, inputs, r.sampleOutput(inputs)));\n"
        "\t\t\t\t}\n"
        "\t\t\t\treturn result;\n"
        "\t\t\tcase 4:\n",
        "QuickRecipe.java: exotic potion reverse guide",
    )

    potion_path.write_text(potion, encoding="utf-8")
    recipe_path.write_text(recipe, encoding="utf-8")
    guide_path.write_text(guide, encoding="utf-8")
    print("patched exotic potion, recipe registration, and alchemy guide")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit(
            "usage: patch_exotic_potion_reverse.py <ExoticPotion.java> <Recipe.java> <QuickRecipe.java>"
        )
    patch(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))
