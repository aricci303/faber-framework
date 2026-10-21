package faber.devl;

/**
 * Opens a project: the folder faber/devl/projects/<project> in the classpath, with project.json and the standard files (CaseData), and the project's code, the DevlCase that project.json names.
 * Nothing is registered and nothing is listed: the framework has no compile-time knowledge of any project, and a new project is a folder and a class.
 */
public final class Projects {
    private Projects() {}

    public static DevlCase open(String project) {
        if (project == null || project.isBlank()) throw new IllegalArgumentException("name the project with --project=<folder> (a folder in resources/faber/devl/projects)");
        return open(CaseData.load(project));
    }

    /** The project's code, built from its data: the class project.json names, with a constructor that takes the CaseData. */
    public static DevlCase open(CaseData data) {
        String cn = data.devlCaseClass();
        if (cn.isBlank()) throw new IllegalArgumentException("project '" + data.id() + "': project.json does not name its class (\"devlCase\": the faber.devl.DevlCase of the project's code)");
        try { return (DevlCase) Class.forName(cn).getDeclaredConstructor(CaseData.class).newInstance(data); }
        catch (ReflectiveOperationException | ClassCastException e) { throw new IllegalArgumentException("project '" + data.id() + "': " + cn + " is not a faber.devl.DevlCase with a constructor that takes a faber.devl.CaseData: " + e); }
    }
}
