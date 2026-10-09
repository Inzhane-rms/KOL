package ph.appbuilders.saklolo.ui

data class PreparednessTip(val tagalog: String, val english: String)

/** Short lines drawn from public disaster-prep guidance. Credited in the README. */
val preparednessTips = listOf(
    PreparednessTip(
        "Huwag tumawid sa baha na lampas tuhod.",
        "Don't cross water above the knee.",
    ),
    PreparednessTip(
        "Lumayo sa mga nahulog na kable ng kuryente.",
        "Stay away from downed power lines.",
    ),
    PreparednessTip(
        "Patayin ang kuryente at gas bago lumikas.",
        "Turn off power and gas before leaving.",
    ),
    PreparednessTip(
        "Lumikas agad kapag may babala ang barangay.",
        "Evacuate when your barangay warns.",
    ),
    PreparednessTip(
        "Iwasan ang ilog, sapa, at dalisdis.",
        "Avoid rivers, creeks, and slopes.",
    ),
    PreparednessTip(
        "I-charge ang phone at powerbank ngayon.",
        "Charge your phone and power bank now.",
    ),
    PreparednessTip(
        "Manatili sa loob at maging kalmado.",
        "Stay indoors and stay calm.",
    ),
    PreparednessTip(
        "Sumunod agad sa utos ng paglikas ng LGU.",
        "Follow your LGU's evacuation order right away.",
    ),
)
