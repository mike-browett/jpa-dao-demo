package com.aixpertsoft.hogwarts.demo;

import com.aixpertsoft.hogwarts.api.HouseRequests;
import com.aixpertsoft.hogwarts.api.ProfessorRequests;
import com.aixpertsoft.hogwarts.api.SpellRequests;
import com.aixpertsoft.hogwarts.api.WizardRequests;
import com.aixpertsoft.hogwarts.api.WizardSpellRequests;
import com.aixpertsoft.hogwarts.entity.SpellCategory;
import com.aixpertsoft.hogwarts.entity.WizardWand;
import com.aixpertsoft.hogwarts.service.HouseService;
import com.aixpertsoft.hogwarts.service.ProfessorService;
import com.aixpertsoft.hogwarts.service.SpellService;
import com.aixpertsoft.hogwarts.service.WizardService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Custom code: fills an empty database with characters and spells from the Harry Potter books,
 * through the generated services so the business rules run too. Switch off with demo.seed=false.
 * Levels are a fan's estimate, not canon.
 */
@Component
@ConditionalOnProperty(name = "demo.seed", havingValue = "true", matchIfMissing = true)
public class DemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    private final HouseService houses;
    private final WizardService wizards;
    private final ProfessorService professors;
    private final SpellService spells;

    private final Map<String, UUID> house = new HashMap<>();
    private final Map<String, UUID> wizard = new HashMap<>();
    private final Map<String, UUID> spell = new HashMap<>();
    private int links;

    public DemoData(HouseService houses, WizardService wizards, ProfessorService professors, SpellService spells) {
        this.houses = houses;
        this.wizards = wizards;
        this.professors = professors;
        this.spells = spells;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (houses.list(0, 1).total() > 0) {
            return;
        }
        houses();
        spells();
        students();
        professors();
        spellbooks();
        log.info("Demo data created: {} houses, {} wizards, {} spells, {} wizard-spell links",
                house.size(), wizard.size(), spell.size(), links);
    }

    private void houses() {
        house("Gryffindor", "Godric Gryffindor", "Lion", "Scarlet and gold", "Nearly Headless Nick");
        house("Hufflepuff", "Helga Hufflepuff", "Badger", "Yellow and black", "The Fat Friar");
        house("Ravenclaw", "Rowena Ravenclaw", "Eagle", "Blue and bronze", "The Grey Lady");
        house("Slytherin", "Salazar Slytherin", "Serpent", "Green and silver", "The Bloody Baron");
    }

    private void spells() {
        spell("Disarming Charm", "Expelliarmus", SpellCategory.CHARM, 2);
        spell("Patronus Charm", "Expecto Patronum", SpellCategory.CHARM, 5);
        spell("Levitation Charm", "Wingardium Leviosa", SpellCategory.CHARM, 1);
        spell("Wand-Lighting Charm", "Lumos", SpellCategory.CHARM, 1);
        spell("Summoning Charm", "Accio", SpellCategory.CHARM, 3);
        spell("Stunning Spell", "Stupefy", SpellCategory.CHARM, 3);
        spell("Unlocking Charm", "Alohomora", SpellCategory.CHARM, 1);
        spell("Mending Charm", "Reparo", SpellCategory.CHARM, 1);
        spell("Memory Charm", "Obliviate", SpellCategory.CHARM, 4);
        spell("Boggart-Banishing Spell", "Riddikulus", SpellCategory.CHARM, 3);
        spell("Animation Charm", "Piertotum Locomotor", SpellCategory.CHARM, 5);
        spell("Leg-Locker Curse", "Locomotor Mortis", SpellCategory.JINX, 2);
        spell("Full Body-Bind Curse", "Petrificus Totalus", SpellCategory.JINX, 2);
        spell("Bat-Bogey Hex", null, SpellCategory.HEX, 3);           // no incantation in the books
        spell("Pimple Jinx", "Furnunculus", SpellCategory.HEX, 2);
        spell("Sectumsempra", "Sectumsempra", SpellCategory.CURSE, 4);
        spell("Dark Mark", "Morsmordre", SpellCategory.CURSE, 4);
        spell("Healing Spell", "Episkey", SpellCategory.HEALING, 2);
        spell("Wound-Sealing Incantation", "Vulnera Sanentur", SpellCategory.HEALING, 4);
        unforgivable("Killing Curse", "Avada Kedavra");
        unforgivable("Cruciatus Curse", "Crucio");
        unforgivable("Imperius Curse", "Imperio");
    }

    private void students() {
        student("Harry Potter", 1980, "Stag", wand("Holly", "Phoenix feather", "11"), "Gryffindor");
        student("Hermione Granger", 1979, "Otter", wand("Vine", "Dragon heartstring", "10.75"), "Gryffindor");
        student("Ron Weasley", 1980, "Jack Russell Terrier", wand("Willow", "Unicorn hair", "14"), "Gryffindor");
        student("Ginny Weasley", 1981, "Horse", null, "Gryffindor");
        student("Neville Longbottom", 1980, null, wand("Cherry", "Unicorn hair", "13"), "Gryffindor");
        student("Luna Lovegood", 1981, "Hare", null, "Ravenclaw");
        student("Cedric Diggory", 1977, null, wand("Ash", "Unicorn hair", "12.25"), "Hufflepuff");
        student("Draco Malfoy", 1980, null, wand("Hawthorn", "Unicorn hair", "10"), "Slytherin");
        student("Bellatrix Lestrange", 1951, null, wand("Walnut", "Dragon heartstring", "12.75"), "Slytherin");
        student("Tom Riddle", 1926, null, wand("Yew", "Phoenix feather", "13.5"), "Slytherin");
    }

    private void professors() {
        professor("Albus Dumbledore", 1881, "Phoenix", wand("Elder", "Thestral tail hair", "15"), "Headmaster", "Gryffindor");
        professor("Minerva McGonagall", 1935, "Cat", wand("Fir", "Dragon heartstring", "9.5"), "Transfiguration", "Gryffindor");
        professor("Remus Lupin", 1960, "Wolf", wand("Cypress", "Unicorn hair", "10.25"), "Defence Against the Dark Arts", "Gryffindor");
        professor("Severus Snape", 1960, "Doe", null, "Potions", "Slytherin");
        professor("Filius Flitwick", null, null, null, "Charms", "Ravenclaw");
        professor("Pomona Sprout", null, null, null, "Herbology", "Hufflepuff");
    }

    private void spellbooks() {
        knows("Harry Potter", "Disarming Charm", 9, true);
        knows("Harry Potter", "Patronus Charm", 8);
        knows("Harry Potter", "Summoning Charm", 6);
        knows("Harry Potter", "Stunning Spell", 7);
        knows("Harry Potter", "Wand-Lighting Charm", 5);
        knows("Harry Potter", "Sectumsempra", 2);
        knows("Harry Potter", "Imperius Curse", 7);              // Gringotts, book 7

        knows("Hermione Granger", "Levitation Charm", 9, true);  // it's Levi-O-sa
        knows("Hermione Granger", "Memory Charm", 7);
        knows("Hermione Granger", "Unlocking Charm", 8);
        knows("Hermione Granger", "Mending Charm", 8);
        knows("Hermione Granger", "Full Body-Bind Curse", 8);
        knows("Hermione Granger", "Patronus Charm", 6);

        knows("Ron Weasley", "Levitation Charm", 6, true);       // the troll, book 1
        knows("Ron Weasley", "Wand-Lighting Charm", 4);
        knows("Ron Weasley", "Disarming Charm", 4);

        knows("Ginny Weasley", "Bat-Bogey Hex", 9, true);
        knows("Ginny Weasley", "Disarming Charm", 6);

        knows("Neville Longbottom", "Boggart-Banishing Spell", 5, true);
        knows("Neville Longbottom", "Disarming Charm", 3);

        knows("Luna Lovegood", "Patronus Charm", 7, true);
        knows("Luna Lovegood", "Stunning Spell", 6);
        knows("Luna Lovegood", "Mending Charm", 5);

        knows("Cedric Diggory", "Summoning Charm", 7, true);
        knows("Cedric Diggory", "Stunning Spell", 6);

        knows("Draco Malfoy", "Disarming Charm", 7, true);       // the Astronomy Tower
        knows("Draco Malfoy", "Dark Mark", 7);
        knows("Draco Malfoy", "Pimple Jinx", 5);

        knows("Bellatrix Lestrange", "Cruciatus Curse", 9, true);
        knows("Bellatrix Lestrange", "Killing Curse", 9);
        knows("Bellatrix Lestrange", "Stunning Spell", 8);

        knows("Tom Riddle", "Killing Curse", 9, true);
        knows("Tom Riddle", "Imperius Curse", 9);
        knows("Tom Riddle", "Cruciatus Curse", 9);
        knows("Tom Riddle", "Dark Mark", 9);

        knows("Albus Dumbledore", "Patronus Charm", 9, true);
        knows("Albus Dumbledore", "Summoning Charm", 9);
        knows("Albus Dumbledore", "Memory Charm", 9);
        knows("Albus Dumbledore", "Healing Spell", 8);

        knows("Minerva McGonagall", "Animation Charm", 9, true); // "I've always wanted to use that spell"
        knows("Minerva McGonagall", "Mending Charm", 9);
        knows("Minerva McGonagall", "Stunning Spell", 8);

        knows("Remus Lupin", "Boggart-Banishing Spell", 9, true);
        knows("Remus Lupin", "Patronus Charm", 9);

        knows("Severus Snape", "Sectumsempra", 9, true);         // he invented it
        knows("Severus Snape", "Wound-Sealing Incantation", 9);
        knows("Severus Snape", "Disarming Charm", 9);
        knows("Severus Snape", "Patronus Charm", 9);
        knows("Severus Snape", "Killing Curse", 9);

        knows("Filius Flitwick", "Levitation Charm", 9, true);
        knows("Filius Flitwick", "Summoning Charm", 9);
        knows("Filius Flitwick", "Wand-Lighting Charm", 9);

        knows("Pomona Sprout", "Healing Spell", 6, true);
    }

    // ---- helpers ----

    private void house(String name, String founder, String animal, String colours, String ghost) {
        house.put(name, houses.create(new HouseRequests.Create(name, founder, animal, colours, ghost)).id());
    }

    private void spell(String name, String incantation, SpellCategory category, int difficulty) {
        spell.put(name, spells.create(new SpellRequests.Create(name, incantation, category, false, difficulty)).id());
    }

    private void unforgivable(String name, String incantation) {
        spell.put(name, spells.create(new SpellRequests.Create(name, incantation, SpellCategory.CURSE, true, 5)).id());
    }

    private void student(String name, Integer born, String patronus, WizardWand wand, String houseName) {
        wizard.put(name, wizards.create(new WizardRequests.Create(name, born, patronus, wand, house.get(houseName))).id());
    }

    private void professor(String name, Integer born, String patronus, WizardWand wand, String subject, String houseName) {
        wizard.put(name, professors.create(
                new ProfessorRequests.Create(name, born, patronus, wand, subject, house.get(houseName))).id());
    }

    private static WizardWand wand(String wood, String core, String lengthInches) {
        return new WizardWand(wood, core, new BigDecimal(lengthInches));
    }

    private void knows(String wizardName, String spellName, int level) {
        knows(wizardName, spellName, level, false);
    }

    private void knows(String wizardName, String spellName, int level, boolean favourite) {
        wizards.addSpell(wizard.get(wizardName), new WizardSpellRequests.Link(spell.get(spellName), level, favourite));
        links++;
    }
}
