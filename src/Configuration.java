import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.sun.istack.internal.Nullable;
import com.intellij.openapi.project.Project;

/**
 * Configuration for this plugin is stored with "Persisting State of Components".
 * @link http://www.jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html
 *
 * @author Benjamin Meyer <b.meyer@performed.ch>
 * @copyright 2016 Performed
 */
@State(name = "PHPCIGenerateModels", storages = {@Storage(value="model_generator.xml", file="model_generator.xml")})
public class Configuration implements PersistentStateComponent<Configuration> {

    /** Url to database. @see java.sql.DriverManager.getConnection */
    public String dbUrl = null;
    /** Username of database user. @see java.sql.DriverManager.getConnection */
    public String dbUsername = null;
    /** Password of database user. @see java.sql.DriverManager.getConnection */
    public String dbPassword = null;

    /** Author inserted in the generated file. */
    public String author = null;

    /**
     * Workaround: This method sets default values outside of the default constructor, so intelliJ generates the xml config file!
     */
    public void initIfDefault() {
        if (dbUrl == null)
            dbUrl = "jdbc:mysql://localhost:3306/photobooth";
        if (dbUsername == null)
            dbUsername = "root";
        if (dbPassword == null)
            dbPassword = "";
        if (author == null)
            author = "Benjamin Meyer <b.meyer@performed.ch>";
    }

    @Nullable
    @Override
    public Configuration getState() {
        return this;
    }

    @Override
    public void loadState(Configuration singleFileExecutionConfig) {
        this.dbUrl = "jdbc:mysql://localhost:3306/photobooth";
        XmlSerializerUtil.copyBean(singleFileExecutionConfig, this);
    }

    @Nullable
    public static Configuration getInstance(Project project) {
        return ServiceManager.getService(project, Configuration.class);
    }

}
