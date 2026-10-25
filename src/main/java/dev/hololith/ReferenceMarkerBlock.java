package dev.hololith;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
public final class ReferenceMarkerBlock extends BaseEntityBlock {
    public static final MapCodec<ReferenceMarkerBlock> CODEC=simpleCodec(ReferenceMarkerBlock::new);
    public static final EnumProperty<DyeColor> COLOR=EnumProperty.create("color",DyeColor.class);
    public ReferenceMarkerBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(COLOR,DyeColor.CYAN));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(COLOR);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return box(5,0,5,11,7.1,11);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ReferenceMarkerEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,Hololith.MARKER_ENTITY.get(),ReferenceMarkerEntity::tick);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(stack.is(Hololith.CARD.get()))return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if(stack.getItem() instanceof DyeItem dye){
            if(!player.mayBuild()||!level.mayInteract(player,pos))return ItemInteractionResult.FAIL;
            if(!level.isClientSide&&state.getValue(COLOR)!=dye.getDyeColor()){
                level.setBlock(pos,state.setValue(COLOR,dye.getDyeColor()),3);stack.consume(1,player);
                SurveyFeedback.success(player,dev.hololith.compat.ShipCoordinates.toWorld(level,pos).orElse(null),"message.hololith.reference_color",SurveyFeedback.colorName(dye.getDyeColor()));
            }return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide&&level.getBlockEntity(pos) instanceof ReferenceMarkerEntity marker)SurveyFeedback.inspectMarker(player,marker);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
