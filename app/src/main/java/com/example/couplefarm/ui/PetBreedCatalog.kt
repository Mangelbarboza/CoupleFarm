package com.example.couplefarm.ui

import androidx.annotation.DrawableRes
import com.example.couplefarm.R
import com.example.couplefarm.game.DomesticAnimalType

/**
 * Catálogo único de razas. Agregar una raza nueva consiste en copiar sus 12 PNG
 * direccionales y sumar una entrada aquí; su IA y comportamiento siguen siendo
 * los mismos de la especie.
 */
data class PetBreedDefinition(
    val type: DomesticAnimalType,
    val breedIndex: Int,
    val spritePrefix: String,
    val displayName: String,
    val price: Int,
    @param:DrawableRes val previewResource: Int,
)

object PetBreedCatalog {
    val dogs = listOf(
        PetBreedDefinition(DomesticAnimalType.DOG, 0, "puppy_gold", "Dorado", 420, R.drawable.puppy_gold_sit_down_v7),
        PetBreedDefinition(DomesticAnimalType.DOG, 1, "puppy_border", "Negro", 420, R.drawable.puppy_border_sit_down_v7),
        PetBreedDefinition(DomesticAnimalType.DOG, 2, "puppy_cream", "Crema", 420, R.drawable.puppy_cream_sit_down_v7),
    )

    val cats = listOf(
        PetBreedDefinition(DomesticAnimalType.CAT, 0, "kitten_orange", "Naranja", 380, R.drawable.kitten_orange_sit_down_v7),
        PetBreedDefinition(DomesticAnimalType.CAT, 1, "kitten_gray", "Gris", 380, R.drawable.kitten_gray_sit_down_v7),
        PetBreedDefinition(DomesticAnimalType.CAT, 2, "kitten_calico", "Calicó", 380, R.drawable.kitten_calico_sit_down_v7),
    )

    val all: List<PetBreedDefinition> = dogs + cats

    fun find(type: DomesticAnimalType, breedIndex: Int): PetBreedDefinition? =
        all.firstOrNull { it.type == type && it.breedIndex == breedIndex }
}
