package bomberman;

import arc.*;
import arc.graphics.Color;
import arc.struct.*;
import arc.util.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.effect.*;
import mindustry.entities.type.Player;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.plugin.*;
import mindustry.game.EventType.*;
import mindustry.core.GameState.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;

import static mindustry.Vars.*;
import static arc.util.Log.info;

public class BombermanMod extends Plugin{
    private final Rules rules = new Rules();

    //remove
    private int counter = 0;

    private BombermanGenerator generator;

    @Override
    public void init(){
        info("senpai bomberman <3");
        rules.tags.put("bomberman", "true");
        rules.infiniteResources = true;
        rules.canGameOver = false;
        //test
        rules.buildSpeedMultiplier = 0.5f;

        //Todo: check for min 2 players and have a countdown (~ 10 seconds)
        //if game is already running -> spectator mode
        Events.on(PlayerJoin.class, event -> {
            if(!active()) return;

            event.player.kill();
            event.player.setTeam(Team.sharded);
            event.player.dead = false;

            //set location
            Call.onPositionSet(event.player.con, generator.spawns[counter][0]*8, generator.spawns[counter][1]*8);
            //event.player.set(34*8, 34*8);
            event.player.setNet(generator.spawns[counter][0]*8, generator.spawns[counter][1]*8);
            event.player.set(generator.spawns[counter][0]*8, generator.spawns[counter][1]*8);
            //this.counter++;
            //player
            event.player.mech = Powerup.yellow.mech;
            event.player.heal();
        });

        //block flying over walls
        Events.on(Trigger.update, () -> {
            for (Player p: playerGroup){
                if (p.getTeam() != Team.sharded) continue;
                if (world.tile(p.tileX(), p.tileY()).block() != Blocks.air){
                    p.sendMessage("[scarlet]FLYING OVER WALLS == CHEATING\ndisqualified!");
                    p.setTeam(Team.green);
                    Call.onPlayerDeath(p);
                }
            }
        });

        //deconstruct wall
        Events.on(BuildSelectEvent.class, event -> {
            if(!event.breaking) return;

            //could be null
            Powerup tmp = generator.pWalls.removeKey(event.tile);
            if(tmp == null) return;

            Player player = (Player) event.builder;
            Log.info(player.mech);
            player.mech = tmp.mech;

        });
        //build reactor
        Events.on(BlockBuildEndEvent.class, event -> {
            if(event.breaking) return;

            if(event.tile.block() == Blocks.thoriumReactor) {
                int amount;
                //explode nuke
                if (event.player.mech == Mechs.alpha){
                    amount = 7;
                } else if (event.player.mech == Mechs.delta){
                    amount = 10;
                } else if (event.player.mech == Mechs.tau){
                    amount = 12;
                } else { //omega
                    amount = 18;
                }
                //TODO add a delay of 500ms
                Call.transferItemTo(Items.thorium, amount , event.player.x, event.player.y, event.tile);
            }
        });

        //destroys the walls
        Events.on(BlockDestroyEvent.class, event -> {
            if(event.tile.block() != Blocks.thoriumReactor) return;
            int x = event.tile.x;
            int y = event.tile.y;
            //delete vertical
            if (!(world.tile(x-3, y).block() == generator.staticwall && world.tile(x+3, y).block() == generator.staticwall)){
                generator.breakable.each(tile -> {
                    if(tile.block() != Blocks.scrapWallHuge) return;
                    if(event.tile.y == tile.y) {
                        /*new method - not in servertestfile
                        tile.removeNet();*/
                        //alternative
                        Time.run(0f, tile.entity::kill);
                        //TODO: draw laser on airtiles
                        tile.getLinkedTilesAs(Blocks.scrapWallLarge, new Array<>()).each(Fire::create);
                    }
                });
            }
            //delete horizontal
            if (!(world.tile(x, y-3).block() == generator.staticwall && world.tile(x, y+3).block() == generator.staticwall)){
                generator.breakable.each(tile -> {
                    if(tile.block() != Blocks.scrapWallHuge) return;
                    if(event.tile.x == tile.x) {
                        /*new method - not in servertestfile
                        tile.removeNet();*/
                        //alternative
                        Time.run(0f, tile.entity::kill);
                        tile.getLinkedTilesAs(Blocks.scrapWallLarge, new Array<>()).each(Fire::create);
                    }
                });
            }
            /*
            generator.breakable.each(tile -> {

                if(tile.block() != Blocks.scrapWallHuge) return;
                if(event.tile.x == tile.x || event.tile.y == tile.y){
                    //new method - not in servertestfile
                    //tile.removeNet();
                    //alternative
                    Time.run(0f, tile.entity::kill);
                    tile.getLinkedTilesAs(Blocks.scrapWallLarge, new Array<>()).each(Fire::create);
                }
            });*/
            //TODO: check if player was in laser/fire
        });
        //what does it do
        netServer.assigner = (player, players) -> Team.sharded;
    }

    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("bomberman", "Begin hosting with the Bomberman gamemode.", args -> {
            logic.reset();
            Log.info("Generating map...");
            world.loadGenerator(generator = new BombermanGenerator());
            info("Map generated.");
            state.rules = rules.copy();
            logic.play();
            netServer.openServer();
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("powerups", "info about the powerups.", (args, player) -> {
            player.sendMessage("blaljajkjkdlqjfkljkjkdjfkljfksjlj");
        });
    }

    public boolean active(){
        return state.rules.tags.getBool("bomberman") && !state.is(State.menu);
    }

    enum Powerup{
        yellow(Mechs.alpha, Blocks.copperWall), //tier 1
        blue  (Mechs.delta, Blocks.titaniumWall), //tier 2
        green (Mechs.tau,   Blocks.plastaniumWall), //tier 3
        orange(Mechs.omega, Blocks.surgeWall); //slowest mech -> fastest explosion


        public final Mech mech;
        public final Block block;

        Powerup(Mech mech, Block block){
            this.mech = mech;
            this.block = block;
        }

        public static Powerup wall(Block block){
            for(Powerup powerup : values()){
                if(powerup.block == block) return powerup;
            }

            return null;
        }
    }
}
