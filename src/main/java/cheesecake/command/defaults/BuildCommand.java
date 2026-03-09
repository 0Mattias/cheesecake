/*
 * This file is part of Cheesecake.
 *
 * Cheesecake is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Cheesecake is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Cheesecake.  If not, see <https://www.gnu.org/licenses/>.
 */

package cheesecake.command.defaults;

import cheesecake.Cheesecake;
import cheesecake.api.ICheesecake;
import cheesecake.api.command.Command;
import cheesecake.api.command.argument.IArgConsumer;
import cheesecake.api.command.datatypes.RelativeBlockPos;
import cheesecake.api.command.datatypes.RelativeFile;
import cheesecake.api.command.exception.CommandException;
import cheesecake.api.command.exception.CommandInvalidStateException;
import cheesecake.api.utils.BetterBlockPos;
import cheesecake.utils.schematic.SchematicSystem;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Stream;

public class BuildCommand extends Command {

    private final File schematicsDir;

    public BuildCommand(ICheesecake cheesecake) {
        super(cheesecake, "build");
        this.schematicsDir = new File(cheesecake.getPlayerContext().minecraft().runDirectory, "schematics");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        final File file0 = args.getDatatypePost(RelativeFile.INSTANCE, schematicsDir).getAbsoluteFile();
        File file = file0;
        if (FilenameUtils.getExtension(file.getAbsolutePath()).isEmpty()) {
            file = new File(file.getAbsolutePath() + "." + Cheesecake.settings().schematicFallbackExtension.value);
        }
        if (!file.exists()) {
            if (file0.exists()) {
                throw new CommandInvalidStateException(String.format(
                        "Cannot load %s because I do not know which schematic format"
                                + " that is. Please rename the file to include the correct"
                                + " file extension.",
                        file));
            }
            throw new CommandInvalidStateException("Cannot find " + file);
        }
        if (!SchematicSystem.INSTANCE.getByFile(file).isPresent()) {
            StringJoiner formats = new StringJoiner(", ");
            SchematicSystem.INSTANCE.getFileExtensions().forEach(formats::add);
            throw new CommandInvalidStateException(String.format(
                    "Unsupported schematic format. Reckognized file extensions are: %s",
                    formats
            ));
        }
        BetterBlockPos origin = ctx.playerFeet();
        BetterBlockPos buildOrigin;
        if (args.hasAny()) {
            args.requireMax(3);
            buildOrigin = args.getDatatypePost(RelativeBlockPos.INSTANCE, origin);
        } else {
            args.requireMax(0);
            buildOrigin = origin;
        }
        boolean success = cheesecake.getBuilderProcess().build(file.getName(), file, buildOrigin);
        if (!success) {
            throw new CommandInvalidStateException("Couldn't load the schematic. Either your schematic is corrupt or this is a bug.");
        }
        logDirect(String.format("Successfully loaded schematic for building\nOrigin: %s", buildOrigin));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return RelativeFile.tabComplete(args, schematicsDir);
        } else if (args.has(2)) {
            args.get();
            return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Build a schematic";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Build a schematic from a file.",
                "",
                "Usage:",
                "> build <filename> - Loads and builds '<filename>.schematic'",
                "> build <filename> <x> <y> <z> - Custom position"
        );
    }
}
