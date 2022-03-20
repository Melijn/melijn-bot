package me.melijn.bot.model

import me.melijn.bot.utils.InferredChoiceEnum

enum class AnimalType(
    vararg val pairs: Pair<AnimalSource, String>
) : InferredChoiceEnum {
    Cat(AnimalSource.TheCatApi to "", AnimalSource.ImgHoard to "cat"),
    Dog(AnimalSource.ImgHoard to "dog"),
    Bird(AnimalSource.SomeRandomApi to "bird"),
    Fish(AnimalSource.ImgHoard to "fish"),
    Chicken(AnimalSource.ImgHoard to "chicken"),
    Alpaca(AnimalSource.Duncte123 to "alpaca"),
    Duck(AnimalSource.RandomDuk to ""),
    Fox(AnimalSource.SomeRandomApi to "fox"),
    Koala(AnimalSource.SomeRandomApi to "koala"),
    Panda(AnimalSource.SomeRandomApi to "panda"),
    Frog(AnimalSource.ImgHoard to "frog"),
    Lynx(AnimalSource.ImgHoard to "lynx"),
    NyanCat(AnimalSource.ImgHoard to "nyancat"),
    Penguin(AnimalSource.ImgHoard to "penguin"),
    Possum(AnimalSource.ImgHoard to "possum"),
    Snek(AnimalSource.ImgHoard to "snek"),
}