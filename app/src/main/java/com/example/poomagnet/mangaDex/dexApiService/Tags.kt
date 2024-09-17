import coil.request.Tags

enum class Tag(val full_name: String){
    Romance("Romance"),
    Magical_Girls("Magical Girls"),
    Boys_Love("Boys' Love"),
    Animals("Animals"),
    Monsters("Monsters"),
    Demons("Demons"),
    Medical("Medical"),
    Cooking("Cooking"),
    Shota("Shota"),
    Full_Color("Full Color"),
    Aliens("Aliens"),
    Doujinshi("Doujinshi"),
    Wuxia("Wuxia"),
    Fantasy("Fantasy"),
    Incest("Incest"),
    Thriller("Thriller"),
    Crime("Crime"),
    Survival("Survival"),
    Office_Workers("Office Workers"),
    Sci_Fi("Sci-Fi"),
    Gyaru("Gyaru"),
    Ghosts("Ghosts"),
    Villainess("Villainess"),
    Post_Apocalyptic("Post-Apocalyptic"),
    Vampires("Vampires"),
    Video_Games("Video Games"),
    Magic("Magic"),
    Crossdressing("Crossdressing"),
    Police("Police"),
    Sports("Sports"),
    Music("Music"),
    Military("Military"),
    Time_Travel("Time Travel"),
    Reincarnation("Reincarnation"),
    Action("Action"),
    Self_Published("Self-Published"),
    Isekai("Isekai"),
    Martial_Arts("Martial Arts"),
    Official_Colored("Official Colored"),
    Loli("Loli"),
    Four_Koma("4-Koma"),
    Horror("Horror"),
    Superhero("Superhero"),
    Drama("Drama"),
    Tragedy("Tragedy"),
    Delinquents("Delinquents"),
    Adventure("Adventure"),
    Harem("Harem"),
    Zombies("Zombies"),
    Mecha("Mecha"),
    Supernatural("Supernatural"),
    Mystery("Mystery"),
    Reverse_Harem("Reverse Harem"),
    Sexual_Violence("Sexual Violence"),
    School_Life("School Life"),
    Anthology("Anthology"),
    Slice_of_Life("Slice of Life"),
    Long_Strip("Long Strip"),
    Comedy("Comedy"),
    Web_Comic("Web Comic"),
    Virtual_Reality("Virtual Reality"),
    Gore("Gore"),
    Oneshot("Oneshot"),
    Mafia("Mafia"),
    Adaptation("Adaptation"),
    Girls_Love("Girls' Love"),
    Monster_Girls("Monster Girls"),
    Award_Winning("Award Winning"),
    Historical("Historical"),
    Psychological("Psychological"),
    Ninja("Ninja"),
    Traditional_Games("Traditional Games"),
    Philosophical("Philosophical"),
    Samurai("Samurai"),
    Genderswap("Genderswap"),
    Fan_Colored("Fan Colored");

    companion object {
        fun fromValue(value: String): Tag? {
            return entries.find { it.full_name == value }
        }
    }
}


enum class Ordering(val msg: String){
    title("order[title]"),
    year("order[year]"),
    createdAt("order[createdAt]"),
    updatedAt("order[updatedAt]"),
    latestChapter("order[latestUploadedChapter]"),
    followedCount("order[followedCount]"),
    relevance("order[relevance]"),
}