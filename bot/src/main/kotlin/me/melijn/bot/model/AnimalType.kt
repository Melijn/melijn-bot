package me.melijn.bot.model

import me.melijn.bot.utils.InferredChoiceEnum

enum class AnimalType(
    vararg val pairs: Pair<AnimalSource, String>
) : InferredChoiceEnum {
    CAT(AnimalSource.TheCatApi to "", AnimalSource.ImgHoard to "cat"),
    DOG(AnimalSource.ImgHoard to "dog"),
    BIRD(AnimalSource.SomeRandomApi to "bird"),
    FISH(AnimalSource.ImgHoard to "fish"),
    CHICKEN(AnimalSource.ImgHoard to "chicken"),
    ALPACA(AnimalSource.Duncte123 to "alpaca"),
    DUCK(AnimalSource.RandomDuk to ""),
    FOX(AnimalSource.SomeRandomApi to "fox"),
    KOALA(AnimalSource.SomeRandomApi to "koala"),
    PANDA(AnimalSource.SomeRandomApi to "panda"),
    FROG(AnimalSource.ImgHoard to "frog"),
    LYNX(AnimalSource.ImgHoard to "lynx"),
    NYAN_CAT(AnimalSource.ImgHoard to "nyancat"),
    PENGUIN(AnimalSource.ImgHoard to "penguin"),
    POSSUM(AnimalSource.ImgHoard to "possum"),
    SNEK(AnimalSource.ImgHoard to "snek"),
}