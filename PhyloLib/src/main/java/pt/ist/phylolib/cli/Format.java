package pt.ist.phylolib.cli;

/**
 * Enumerates the available formats with their respective regex.
 */
public enum Format {

    FILE("^\\w+:.+$"),
    NATURAL("^\\d+$"),
    DISTANCE("^(\\d*(\\.\\d+(E-\\d+)?)?)$"),
    FLAG("^(?:true|false)?$");

    private final String regex;

    /**
     * Creates a format representing a specific type of data for a given regex.
     *
     * @param regex the regex corresponding to this format
     */
    Format(String regex) {
        this.regex = regex;
    }

    /**
     * Checks whether a String matches this format.
     *
     * @param data the string to be checked
     * @return true if the string matches this format, false otherwise
     */
    public boolean matches(String data) {
        return data.matches(regex);
    }

}
