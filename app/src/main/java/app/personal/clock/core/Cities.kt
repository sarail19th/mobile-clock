package app.personal.clock.core

/** A built-in city: shown by name even when it shares a time zone with another (Marseille and Paris are both Europe/Paris). */
class City(val name: String, val countryCode: String, val zoneId: String)

/** Major world cities for the World tab's search. Order is by region, not by name. */
object Cities {
    val all: List<City> by lazy {
        RAW.mapNotNull { line ->
            val p = line.split('|')
            if (p.size == 3) City(p[0], p[1], p[2]) else null
        }
    }

    private val RAW = """
Paris|FR|Europe/Paris
Marseille|FR|Europe/Paris
Lyon|FR|Europe/Paris
Toulouse|FR|Europe/Paris
Nice|FR|Europe/Paris
Bordeaux|FR|Europe/Paris
Strasbourg|FR|Europe/Paris
Beijing|CN|Asia/Shanghai
Shanghai|CN|Asia/Shanghai
Guangzhou|CN|Asia/Shanghai
Shenzhen|CN|Asia/Shanghai
Chengdu|CN|Asia/Shanghai
Chongqing|CN|Asia/Shanghai
Wuhan|CN|Asia/Shanghai
Xi'an|CN|Asia/Shanghai
Hangzhou|CN|Asia/Shanghai
Tianjin|CN|Asia/Shanghai
Nanjing|CN|Asia/Shanghai
Harbin|CN|Asia/Shanghai
Urumqi|CN|Asia/Urumqi
Hong Kong|HK|Asia/Hong_Kong
Macau|MO|Asia/Macau
Taipei|TW|Asia/Taipei
Bangkok|TH|Asia/Bangkok
Chiang Mai|TH|Asia/Bangkok
Phuket|TH|Asia/Bangkok
Pattaya|TH|Asia/Bangkok
Khon Kaen|TH|Asia/Bangkok
Hat Yai|TH|Asia/Bangkok
Tokyo|JP|Asia/Tokyo
Osaka|JP|Asia/Tokyo
Kyoto|JP|Asia/Tokyo
Nagoya|JP|Asia/Tokyo
Sapporo|JP|Asia/Tokyo
Fukuoka|JP|Asia/Tokyo
Yokohama|JP|Asia/Tokyo
Naha|JP|Asia/Tokyo
Seoul|KR|Asia/Seoul
Busan|KR|Asia/Seoul
Pyongyang|KP|Asia/Pyongyang
Ulaanbaatar|MN|Asia/Ulaanbaatar
Mumbai|IN|Asia/Kolkata
Delhi|IN|Asia/Kolkata
Bengaluru|IN|Asia/Kolkata
Chennai|IN|Asia/Kolkata
Kolkata|IN|Asia/Kolkata
Hyderabad|IN|Asia/Kolkata
Singapore|SG|Asia/Singapore
Kuala Lumpur|MY|Asia/Kuala_Lumpur
Penang|MY|Asia/Kuala_Lumpur
Jakarta|ID|Asia/Jakarta
Bali|ID|Asia/Makassar
Surabaya|ID|Asia/Jakarta
Manila|PH|Asia/Manila
Cebu|PH|Asia/Manila
Hanoi|VN|Asia/Ho_Chi_Minh
Ho Chi Minh City|VN|Asia/Ho_Chi_Minh
Da Nang|VN|Asia/Ho_Chi_Minh
Phnom Penh|KH|Asia/Phnom_Penh
Siem Reap|KH|Asia/Phnom_Penh
Vientiane|LA|Asia/Vientiane
Yangon|MM|Asia/Yangon
Bandar Seri Begawan|BN|Asia/Brunei
Dhaka|BD|Asia/Dhaka
Kathmandu|NP|Asia/Kathmandu
Colombo|LK|Asia/Colombo
Malé|MV|Indian/Maldives
Karachi|PK|Asia/Karachi
Lahore|PK|Asia/Karachi
Islamabad|PK|Asia/Karachi
Kabul|AF|Asia/Kabul
Tashkent|UZ|Asia/Tashkent
Almaty|KZ|Asia/Almaty
Tehran|IR|Asia/Tehran
Baghdad|IQ|Asia/Baghdad
Riyadh|SA|Asia/Riyadh
Jeddah|SA|Asia/Riyadh
Mecca|SA|Asia/Riyadh
Dubai|AE|Asia/Dubai
Abu Dhabi|AE|Asia/Dubai
Doha|QA|Asia/Qatar
Kuwait City|KW|Asia/Kuwait
Manama|BH|Asia/Bahrain
Muscat|OM|Asia/Muscat
Tel Aviv|IL|Asia/Jerusalem
Jerusalem|IL|Asia/Jerusalem
Beirut|LB|Asia/Beirut
Amman|JO|Asia/Amman
Damascus|SY|Asia/Damascus
Istanbul|TR|Europe/Istanbul
Ankara|TR|Europe/Istanbul
Tbilisi|GE|Asia/Tbilisi
Yerevan|AM|Asia/Yerevan
Baku|AZ|Asia/Baku
London|GB|Europe/London
Manchester|GB|Europe/London
Edinburgh|GB|Europe/London
Birmingham|GB|Europe/London
Glasgow|GB|Europe/London
Dublin|IE|Europe/Dublin
Lisbon|PT|Europe/Lisbon
Porto|PT|Europe/Lisbon
Madrid|ES|Europe/Madrid
Barcelona|ES|Europe/Madrid
Valencia|ES|Europe/Madrid
Seville|ES|Europe/Madrid
Rome|IT|Europe/Rome
Milan|IT|Europe/Rome
Naples|IT|Europe/Rome
Venice|IT|Europe/Rome
Florence|IT|Europe/Rome
Berlin|DE|Europe/Berlin
Munich|DE|Europe/Berlin
Frankfurt|DE|Europe/Berlin
Hamburg|DE|Europe/Berlin
Cologne|DE|Europe/Berlin
Amsterdam|NL|Europe/Amsterdam
Rotterdam|NL|Europe/Amsterdam
Brussels|BE|Europe/Brussels
Luxembourg|LU|Europe/Luxembourg
Zurich|CH|Europe/Zurich
Geneva|CH|Europe/Zurich
Bern|CH|Europe/Zurich
Vienna|AT|Europe/Vienna
Prague|CZ|Europe/Prague
Warsaw|PL|Europe/Warsaw
Krakow|PL|Europe/Warsaw
Budapest|HU|Europe/Budapest
Bucharest|RO|Europe/Bucharest
Sofia|BG|Europe/Sofia
Athens|GR|Europe/Athens
Belgrade|RS|Europe/Belgrade
Zagreb|HR|Europe/Zagreb
Ljubljana|SI|Europe/Ljubljana
Bratislava|SK|Europe/Bratislava
Sarajevo|BA|Europe/Sarajevo
Tirana|AL|Europe/Tirane
Valletta|MT|Europe/Malta
Monaco|MC|Europe/Monaco
Copenhagen|DK|Europe/Copenhagen
Stockholm|SE|Europe/Stockholm
Oslo|NO|Europe/Oslo
Helsinki|FI|Europe/Helsinki
Reykjavik|IS|Atlantic/Reykjavik
Tallinn|EE|Europe/Tallinn
Riga|LV|Europe/Riga
Vilnius|LT|Europe/Vilnius
Kyiv|UA|Europe/Kyiv
Minsk|BY|Europe/Minsk
Moscow|RU|Europe/Moscow
Saint Petersburg|RU|Europe/Moscow
Yekaterinburg|RU|Asia/Yekaterinburg
Novosibirsk|RU|Asia/Novosibirsk
Vladivostok|RU|Asia/Vladivostok
Cairo|EG|Africa/Cairo
Alexandria|EG|Africa/Cairo
Lagos|NG|Africa/Lagos
Abuja|NG|Africa/Lagos
Nairobi|KE|Africa/Nairobi
Addis Ababa|ET|Africa/Addis_Ababa
Johannesburg|ZA|Africa/Johannesburg
Cape Town|ZA|Africa/Johannesburg
Durban|ZA|Africa/Johannesburg
Casablanca|MA|Africa/Casablanca
Rabat|MA|Africa/Casablanca
Marrakesh|MA|Africa/Casablanca
Algiers|DZ|Africa/Algiers
Tunis|TN|Africa/Tunis
Tripoli|LY|Africa/Tripoli
Accra|GH|Africa/Accra
Dakar|SN|Africa/Dakar
Kinshasa|CD|Africa/Kinshasa
Luanda|AO|Africa/Luanda
Dar es Salaam|TZ|Africa/Dar_es_Salaam
Kampala|UG|Africa/Kampala
Khartoum|SD|Africa/Khartoum
Harare|ZW|Africa/Harare
Lusaka|ZM|Africa/Lusaka
Maputo|MZ|Africa/Maputo
Antananarivo|MG|Indian/Antananarivo
Port Louis|MU|Indian/Mauritius
New York|US|America/New_York
Boston|US|America/New_York
Washington DC|US|America/New_York
Miami|US|America/New_York
Atlanta|US|America/New_York
Orlando|US|America/New_York
Chicago|US|America/Chicago
Houston|US|America/Chicago
Dallas|US|America/Chicago
Austin|US|America/Chicago
New Orleans|US|America/Chicago
Denver|US|America/Denver
Phoenix|US|America/Phoenix
Los Angeles|US|America/Los_Angeles
San Francisco|US|America/Los_Angeles
San Diego|US|America/Los_Angeles
Seattle|US|America/Los_Angeles
Las Vegas|US|America/Los_Angeles
Portland|US|America/Los_Angeles
Anchorage|US|America/Anchorage
Honolulu|US|Pacific/Honolulu
Toronto|CA|America/Toronto
Montreal|CA|America/Toronto
Ottawa|CA|America/Toronto
Vancouver|CA|America/Vancouver
Calgary|CA|America/Edmonton
Winnipeg|CA|America/Winnipeg
Halifax|CA|America/Halifax
Mexico City|MX|America/Mexico_City
Guadalajara|MX|America/Mexico_City
Cancun|MX|America/Cancun
Havana|CU|America/Havana
Panama City|PA|America/Panama
San Jose|CR|America/Costa_Rica
Guatemala City|GT|America/Guatemala
Kingston|JM|America/Jamaica
Santo Domingo|DO|America/Santo_Domingo
San Juan|PR|America/Puerto_Rico
Bogota|CO|America/Bogota
Medellin|CO|America/Bogota
Lima|PE|America/Lima
Quito|EC|America/Guayaquil
Caracas|VE|America/Caracas
Santiago|CL|America/Santiago
Buenos Aires|AR|America/Argentina/Buenos_Aires
Sao Paulo|BR|America/Sao_Paulo
Rio de Janeiro|BR|America/Sao_Paulo
Brasilia|BR|America/Sao_Paulo
Manaus|BR|America/Manaus
Montevideo|UY|America/Montevideo
Asuncion|PY|America/Asuncion
La Paz|BO|America/La_Paz
Sydney|AU|Australia/Sydney
Melbourne|AU|Australia/Melbourne
Canberra|AU|Australia/Sydney
Brisbane|AU|Australia/Brisbane
Perth|AU|Australia/Perth
Adelaide|AU|Australia/Adelaide
Darwin|AU|Australia/Darwin
Hobart|AU|Australia/Hobart
Auckland|NZ|Pacific/Auckland
Wellington|NZ|Pacific/Auckland
Suva|FJ|Pacific/Fiji
Papeete|PF|Pacific/Tahiti
Guam|GU|Pacific/Guam
Port Moresby|PG|Pacific/Port_Moresby
""".trimIndent().lines()
}

/**
 * A saved World-tab entry is either a plain time-zone id ("Europe/Paris") or "zone|label" ("Europe/Paris|Marseille"),
 * so a city keeps the name the user picked even though several cities share one zone.
 */
object WorldEntry {
    fun zone(entry: String): String = entry.substringBefore('|')

    fun label(entry: String): String = entry.substringAfter('|', "").ifBlank { Fmt.cityName(zone(entry)) }

    fun make(zoneId: String, label: String): String =
        if (label.isBlank() || label == Fmt.cityName(zoneId)) zoneId else "$zoneId|$label"
}
