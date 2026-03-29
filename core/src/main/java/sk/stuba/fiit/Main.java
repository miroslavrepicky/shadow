package sk.stuba.fiit;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;

public class Main extends ApplicationAdapter {

    // Box2D pracuje v metroch, nie pixeloch
    // PPM = pixels per meter
    private static final float PPM = 16f;

    private TiledMap map;
    private OrthogonalTiledMapRenderer renderer;
    private OrthographicCamera camera;

    private SpriteBatch batch;
    private Texture walkSheet;
    private Texture idleSheet;
    private Animation<TextureRegion> walkAnimation;
    private Animation<TextureRegion> idleAnimation;

    private World world;
    private Body playerBody;
    private Box2DDebugRenderer debugRenderer;

    private boolean facingRight = true;
    private float stateTime = 0f;
    private float speed = 3f; // v metroch/s

    private static final int FRAME_WIDTH = 64;
    private static final int FRAME_HEIGHT = 64;

    @Override
    public void create() {
        map = new TmxMapLoader().load("test_map.tmx");
        renderer = new OrthogonalTiledMapRenderer(map);
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 800, 600);

        batch = new SpriteBatch();

        walkSheet = new Texture("duck/walk.png");
        TextureRegion[] walkFrames = new TextureRegion[4];
        for (int i = 0; i < 4; i++) {
            walkFrames[i] = new TextureRegion(walkSheet, i * FRAME_WIDTH, 0, FRAME_WIDTH, FRAME_HEIGHT);
        }
        walkAnimation = new Animation<>(0.15f, walkFrames);

        idleSheet = new Texture("duck/idle.png");
        TextureRegion[] idleFrames = new TextureRegion[2];
        for (int i = 0; i < 2; i++) {
            idleFrames[i] = new TextureRegion(idleSheet, i * FRAME_WIDTH, 0, FRAME_WIDTH, FRAME_HEIGHT);
        }
        idleAnimation = new Animation<>(0.3f, idleFrames);

        // Box2D svet s gravitáciou
        world = new World(new Vector2(0, -10f), true);
        debugRenderer = new Box2DDebugRenderer();

        // Hráč
        BodyDef playerDef = new BodyDef();
        playerDef.type = BodyDef.BodyType.DynamicBody;
        playerDef.position.set(100 / PPM, 800 / PPM);
        playerDef.fixedRotation = true; // aby sa nekotúľal

        playerBody = world.createBody(playerDef);

        PolygonShape playerShape = new PolygonShape();
        playerShape.setAsBox(8 / PPM, 8 / PPM); // 16x16 hitbox

        FixtureDef playerFixture = new FixtureDef();
        playerFixture.shape = playerShape;
        playerFixture.density = 1f;
        playerFixture.friction = 0f; // bez trenia aby nelpil na stenách
        playerFixture.restitution = 0f;
        playerBody.createFixture(playerFixture);
        playerShape.dispose();

        // Kolízie z object layeru
        float tileHeight = map.getProperties().get("tileheight", Integer.class);
        float mapHeight = map.getProperties().get("height", Integer.class) * tileHeight;
        MapLayer collisionLayer = map.getLayers().get("hitbox");
        for (MapObject object : collisionLayer.getObjects()) {
            if (object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();

                // Oprava Y osi z Tiled
                float fixedY = rect.y;

                BodyDef wallDef = new BodyDef();
                wallDef.type = BodyDef.BodyType.StaticBody;
                wallDef.position.set(
                    (rect.x + rect.width / 2) / PPM,
                    (fixedY + rect.height / 2) / PPM
                );

                Body wallBody = world.createBody(wallDef);

                PolygonShape wallShape = new PolygonShape();
                wallShape.setAsBox(rect.width / 2 / PPM, rect.height / 2 / PPM);
                wallBody.createFixture(wallShape, 0f);
                wallShape.dispose();
            }
        }
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        stateTime += delta;

        // Automatický pohyb
        float velX = facingRight ? speed : -speed;
        playerBody.setLinearVelocity(velX, playerBody.getLinearVelocity().y);

        // Otočenie pri hranici mapy alebo stene
        float tileWidth = map.getProperties().get("tilewidth", Integer.class);
        float mapWidth = map.getProperties().get("width", Integer.class) * tileWidth;
        float px = playerBody.getPosition().x * PPM;
        if (px > mapWidth - FRAME_WIDTH) facingRight = false;
        if (px < 0) facingRight = true;

        // Box2D krok
        world.step(delta, 6, 2);

        // Kamera sleduje hráča
        float camX = Math.max(400, Math.min(px, mapWidth - 400));
        camera.position.set(camX, 300, 0);
        camera.update();

        // Render
        Gdx.gl.glClearColor(0.15f, 0.15f, 0.2f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        renderer.setView(camera);
        renderer.render();

        // Sprite – pozícia z Box2D body
        float spriteX = playerBody.getPosition().x * PPM - FRAME_WIDTH / 2f;
        float spriteY = playerBody.getPosition().y * PPM - FRAME_HEIGHT / 2f;

        TextureRegion currentFrame = walkAnimation.getKeyFrame(stateTime, true);
        TextureRegion frame = new TextureRegion(currentFrame);
        if (!facingRight && !frame.isFlipX()) frame.flip(true, false);
        if (facingRight && frame.isFlipX()) frame.flip(true, false);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.draw(frame, spriteX, spriteY, FRAME_WIDTH, FRAME_HEIGHT);
        batch.end();

        // Box2D debug – zobrazí hitboxy, zmaž keď bude všetko sedieť
        debugRenderer.render(world, camera.combined.cpy().scale(PPM, PPM, 1));
    }

    @Override
    public void dispose() {
        map.dispose();
        renderer.dispose();
        batch.dispose();
        walkSheet.dispose();
        idleSheet.dispose();
        world.dispose();
        debugRenderer.dispose();
    }
}
