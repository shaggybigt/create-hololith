package dev.hololith;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class HololithTableBlock extends BaseEntityBlock {
    public static final MapCodec<HololithTableBlock> CODEC=simpleCodec(HololithTableBlock::new);
    public static final IntegerProperty PART=IntegerProperty.create("part",0,8);
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    private static final ThreadLocal<Boolean> REMOVING=ThreadLocal.withInitial(()->false);
    private static final VoxelShape SHAPE=Shapes.or(box(0,12,0,16,16,16),box(2,0,2,14,12,14));
    public HololithTableBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(PART,4).setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(PART,FACING);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){
        int p=state.getValue(PART),x=p%3-1,z=p/3-1;
        int rx=x,rz=z;
        switch(rotation){case CLOCKWISE_90->{rx=-z;rz=x;}case CLOCKWISE_180->{rx=-x;rz=-z;}case COUNTERCLOCKWISE_90->{rx=z;rz=-x;}default->{}}
        return state.setValue(PART,(rz+1)*3+rx+1).setValue(FACING,rotation.rotate(state.getValue(FACING)));
    }
    @Override protected BlockState mirror(BlockState state,Mirror mirror){
        int p=state.getValue(PART),x=p%3-1,z=p/3-1;
        if(mirror==Mirror.FRONT_BACK)x=-x;else if(mirror==Mirror.LEFT_RIGHT)z=-z;
        return state.setValue(PART,(z+1)*3+x+1).setValue(FACING,mirror.mirror(state.getValue(FACING)));
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    public static BlockPos anchor(BlockPos pos,BlockState state){int part=state.getValue(PART);return pos.offset(1-part%3,0,1-part/3);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        BlockPos center=context.getClickedPos();Level level=context.getLevel();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            BlockPos p=center.offset(x,0,z);
            if(!level.getWorldBorder().isWithinBounds(p)||!level.getBlockState(p).canBeReplaced(context)||!level.getFluidState(p).isEmpty())return null;
            BlockState candidate=defaultBlockState().setValue(PART,(z+1)*3+x+1);
            if(!level.isUnobstructed(candidate,p,CollisionContext.empty())||(context.getPlayer()!=null&&!level.mayInteract(context.getPlayer(),p)))return null;
        }
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(level.isClientSide)return;
        for(int part=0;part<9;part++)if(part!=4)level.setBlock(pos.offset(part%3-1,0,part/3-1),state.setValue(PART,part),3);
    }
    private void removeOtherTiles(BlockState state,Level level,BlockPos pos,boolean dropCenter){
        // Assembly relocates blocks through onRemove; never tear down a multiblock there.
        if(!level.isClientSide&&!REMOVING.get()){
            REMOVING.set(true);
            try {
                BlockPos center=anchor(pos,state);
                for(int part=0;part<9;part++){
                    BlockPos p=center.offset(part%3-1,0,part/3-1);if(p.equals(pos))continue;
                    BlockState other=level.getBlockState(p);
                    if(other.is(this)&&other.getValue(PART)==part)level.destroyBlock(p,part==4&&dropCenter);
                }
            }finally{REMOVING.set(false);}
        }
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        removeOtherTiles(state,level,pos,!player.isCreative()&&player.hasCorrectToolForDrops(state));return super.playerWillDestroy(level,pos,state,player);
    }
    @Override public void onBlockExploded(BlockState state,Level level,BlockPos pos,Explosion explosion){
        removeOtherTiles(state,level,pos,true);super.onBlockExploded(state,level,pos,explosion);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return state.getValue(PART)==4?new HololithTableEntity(pos,state):null;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide||state.getValue(PART)!=4?null:createTickerHelper(type,Hololith.TABLE_ENTITY.get(),HololithTableEntity::tick);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide&&player instanceof ServerPlayer sp&&level.getBlockEntity(anchor(pos,state)) instanceof HololithTableEntity table)HololithNetwork.open(sp,table);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(stack.is(Hololith.CARD.get()))return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("create:wrench")){
            if(!level.isClientSide&&level.getBlockEntity(anchor(pos,state)) instanceof HololithTableEntity table&&table.allowControl()){table.adjustDial(player.isShiftKeyDown());SurveyFeedback.dial(player,table,player.isShiftKeyDown());}
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override public PushReaction getPistonPushReaction(BlockState state){return PushReaction.BLOCK;}
}
