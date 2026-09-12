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

/**
 * A pathfinder and raytracer for flying the Nether by elytra: babbaj's nether-pathfinder
 * (https://github.com/babbaj/nether-pathfinder), which Baritone loads as a native library through
 * JNI, in plain Java. Each class is the C++ file of the same name, ported one for one, and the
 * tests hold the terrain generator and the raytracer to the native library's own recorded answers
 * over a few hundred generated chunks and a few thousand rays. Nothing in here refers to
 * Minecraft; the seam is {@link cheesecake.process.elytra.NetherPathfinderContext}, which packs
 * the game's chunks into {@link cheesecake.process.elytra.pathfinder.Chunk}s and asks
 * {@link cheesecake.process.elytra.pathfinder.NetherPathfinder} for paths and lines of sight.
 * The port was written for babbaj/nether-pathfinder#31.
 */
package cheesecake.process.elytra.pathfinder;
